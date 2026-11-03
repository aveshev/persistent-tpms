import AudioToolbox
import AVFoundation
import Foundation
import TPMSCore

/// Says the red and critical alerts or plays their tones, and vibrates, like the Android app's
/// AlertSpeaker, see docs/alerts.md: a red one twice at each of its readings, then every alert in one
/// of two loops. The critical ones are said every 20 seconds for 10 minutes after their latest
/// reading, then every 10 minutes along with the red ones. Everything goes one at a time, nothing
/// talks over anything. In the background iOS only lets the app run when a sensor is heard, so the
/// loops only go on then.
final class AlertSpeaker: ObservableObject {
    private struct Repeated {
        let alertClass: AlertClass
        /// When a critical alert leaves its loop for the reminders, nil for a red one
        let crimsonUntil: Date?

        var isCrimson: Bool { crimsonUntil != nil }
        func isCrimsonLoop(_ now: Date) -> Bool { crimsonUntil.map { $0 > now } ?? false }
        var phrase: String { alertClass.phrase(isCrimson: isCrimson) }
    }

    private struct Announcement {
        let tag: String
        let phrase: String
        let isCrimson: Bool
    }

    /// The highest level yet to be said, nil once there's nothing left: what can be silenced
    @Published private(set) var level: AlertLevel?
    /// The alerts up to its level aren't said until then, nor kept to be said later
    @Published private(set) var silence: (level: AlertLevel, until: Date)?

    private var settings: Settings?
    private var announcements = [Announcement]()
    /// By tag, a tyre and an alert class
    private var repeated = [String: Repeated]()
    private var nextCrimson: Date?
    private var nextReminder: Date?
    private var loop: Task<Void, Never>?
    private lazy var voice = Voice()

    private static let crimsonPeriod: TimeInterval = 20
    private static let crimsonDuration: TimeInterval = 10 * 60
    private static let reminderPeriod: TimeInterval = 10 * 60
    static let silenceDuration: TimeInterval = 10 * 60

    func configure(settings: Settings) {
        self.settings = settings
    }

    private var isOn: Bool {
        settings.map { $0.alertSound != .none || $0.vibrateAlerts } ?? false
    }

    /// A reading's [levels], only from readings just heard: an old alert isn't said again at launch
    func update(tag: String, levels: [AlertClass: AlertLevel]) {
        for alertClass in [AlertClass.pressure, .temperature, .battery] {
            let classTag = "\(tag)-\(alertClass.rawValue)"
            switch levels[alertClass] {
            case .crimson: crimson(classTag, alertClass)
            case .red: red(classTag, alertClass)
            default: stop(classTag)
            }
        }
    }

    private func red(_ tag: String, _ alertClass: AlertClass) {
        guard isOn else { return }
        announcements.append(Announcement(tag: tag, phrase: alertClass.phrase(isCrimson: false), isCrimson: false))
        repeated[tag] = Repeated(alertClass: alertClass, crimsonUntil: nil)
        scheduled()
    }

    /// A new one is said right away, one already in its loop starts its duration over
    private func crimson(_ tag: String, _ alertClass: AlertClass) {
        guard isOn else { return }
        let now = Date()
        // Its red announcement yet to be said would only come before it
        announcements.removeAll { $0.tag == tag }
        if repeated[tag]?.isCrimsonLoop(now) != true { nextCrimson = nil }
        repeated[tag] = Repeated(alertClass: alertClass, crimsonUntil: now.addingTimeInterval(Self.crimsonDuration))
        scheduled()
    }

    /// Every alert of [tag], a tyre, or just one of them
    func stop(tag: String) {
        for key in repeated.keys where key.hasPrefix(tag) { repeated[key] = nil }
        announcements.removeAll { $0.tag.hasPrefix(tag) }
        scheduled()
    }

    private func stop(_ classTag: String) {
        repeated[classTag] = nil
        scheduled()
    }

    func stopAll() {
        announcements = []
        repeated = [:]
        scheduled()
    }

    func silenceNow() {
        guard let level else { return }
        silence = (level, Date().addingTimeInterval(Self.silenceDuration))
        scheduled()
    }

    func unmute() {
        silence = nil
    }

    /// Drops the silenced alerts. The reminders start a period after something enters them, and
    /// over once they empty.
    private func scheduled() {
        let now = Date()
        if let current = silence, current.until <= now { silence = nil }
        switch silence?.level {
        case .red?:
            repeated = repeated.filter { $0.value.isCrimson }
            announcements.removeAll { $0.isCrimson == false }
        case .some:
            repeated = [:]
            announcements = []
        case nil: break
        }
        nextReminder = reminders(now).isEmpty ? nil : nextReminder ?? now.addingTimeInterval(Self.reminderPeriod)
        let isCrimson = announcements.contains(where: \.isCrimson) || repeated.values.contains(where: \.isCrimson)
        let next: AlertLevel? = isCrimson ? .crimson : announcements.isEmpty && repeated.isEmpty ? nil : .red
        if next != level { level = next }
        if next != nil, loop == nil {
            loop = Task { @MainActor [weak self] in await self?.speakQueue() }
        }
    }

    private func crimsons(_ now: Date) -> [Repeated] {
        repeated.values.filter { $0.isCrimsonLoop(now) }
    }

    /// The critical alerts done with their own loop, and the red ones of the classes no critical
    /// alert is said for
    private func reminders(_ now: Date) -> [Repeated] {
        let crimsonClasses = Set(repeated.values.filter(\.isCrimson).map(\.alertClass))
        return repeated.values.filter {
            $0.isCrimson ? $0.isCrimsonLoop(now) == false : crimsonClasses.contains($0.alertClass) == false
        }
    }

    /// Until there's nothing left to say. Checks every second rather than waiting for the next
    /// phrase: a sleep's clock stops while the phone sleeps.
    @MainActor private func speakQueue() async {
        while Task.isCancelled == false {
            scheduled()
            let now = Date()
            if let announcement = announcements.first {
                announcements.removeAll { $0.phrase == announcement.phrase }
                await say("\(announcement.phrase). \(announcement.phrase).", isCrimson: announcement.isCrimson)
            } else if crimsons(now).isEmpty == false, nextCrimson.map({ $0 <= now }) ?? true {
                // Every period from the start of the phrases, however long they take
                nextCrimson = now.addingTimeInterval(Self.crimsonPeriod)
                await say(spoken(crimsons(now)), isCrimson: true)
            } else if let next = nextReminder, next <= now {
                nextReminder = now.addingTimeInterval(Self.reminderPeriod)
                let reminders = reminders(now)
                if reminders.isEmpty == false { await say(spoken(reminders), isCrimson: reminders.contains(where: \.isCrimson)) }
            } else if level == nil {
                break
            } else {
                try? await Task.sleep(for: .seconds(1))
            }
        }
        loop = nil
    }

    /// Every phrase once, the critical ones first, the whole said twice
    private func spoken(_ alerts: [Repeated]) -> String {
        var phrases = [String]()
        for alert in alerts.sorted(by: { $0.isCrimson && $1.isCrimson == false }) where phrases.contains(alert.phrase) == false {
            phrases.append(alert.phrase)
        }
        let once = phrases.joined(separator: ", ")
        return "\(once). \(once)."
    }

    @MainActor private func say(_ text: String, isCrimson: Bool) async {
        guard let settings else { return }
        if settings.vibrateAlerts { Voice.vibrate(isCrimson: isCrimson) }
        switch settings.alertSound {
        case .speech: await voice.speak(text)
        case .tones: await voice.playTones(isCrimson: isCrimson)
        case .none: break
        }
    }
}

private extension AlertClass {
    func phrase(isCrimson: Bool) -> String {
        if Voice.speaksRussian {
            return switch self {
            case .pressure: isCrimson ? "Критическое давление в шине" : "Давление в шине"
            case .temperature: isCrimson ? "Критический перегрев шины" : "Перегрев шины"
            case .battery: isCrimson ? "Критический заряд батареи в сенсоре" : "Батарея в сенсоре"
            case .pressureLoss, .sensorAlarm: ""
            }
        }
        let phrase = switch self {
        case .pressure: "Tyre pressure"
        case .temperature: "Tyre hot"
        case .battery: "Sensor battery"
        case .pressureLoss, .sensorAlarm: ""
        }
        return isCrimson ? "\(phrase) critical" : phrase
    }
}

/// The phrases and tone patterns, lowering what's playing meanwhile as navigation prompts do
private final class Voice: NSObject, AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private var finished: CheckedContinuation<Void, Never>?

    /// In Russian when that's the app's language and the phone has a Russian voice, in English otherwise
    static var speaksRussian: Bool {
        AppLanguage.current.isRussian && AVSpeechSynthesisVoice(language: "ru-RU") != nil
    }

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    @MainActor func speak(_ text: String) async {
        activate()
        await withCheckedContinuation { continuation in
            finished = continuation
            let utterance = AVSpeechUtterance(string: text)
            utterance.voice = AVSpeechSynthesisVoice(language: Self.speaksRussian ? "ru-RU" : "en-US")
            synthesizer.speak(utterance)
        }
        deactivate()
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) { resume() }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) { resume() }

    private func resume() {
        finished?.resume()
        finished = nil
    }

    /// Like the Android app's AlertTones, shaped after the medical alarms': a medium priority's 3
    /// pulses twice for a red alert, a high priority's fast burst for a critical one
    @MainActor func playTones(isCrimson: Bool) async {
        let (starts, pulse, frequency): ([Int], Int, Double) = isCrimson
            ? ({ burst in burst + burst.map { $0 + 1600 } }([0, 200, 400, 800, 1000]), 120, 880)
            : ({ burst in burst + burst.map { $0 + 1600 } }([0, 300, 600]), 200, 523)
        let sampleRate = 44_100.0
        guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1) else { return }
        let total = Int(Double((starts.max() ?? 0) + pulse) / 1000 * sampleRate)
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(total)),
              let samples = buffer.floatChannelData?[0]
        else { return }
        buffer.frameLength = AVAudioFrameCount(total)
        // A pulse is a tone with a few harmonics, carrying through road noise and a helmet better
        let harmonics = [1.0, 0.5, 0.33, 0.25]
        let pulseLength = Int(Double(pulse) / 1000 * sampleRate), ramp = 0.01 * sampleRate
        for start in starts {
            let first = Int(Double(start) / 1000 * sampleRate)
            for i in 0..<pulseLength where first + i < total {
                // Ramps in and out, a click otherwise
                let envelope = min(1, Double(i) / ramp, Double(pulseLength - i) / ramp)
                let time = Double(i) / sampleRate
                let wave = harmonics.enumerated().reduce(0.0) { $0 + $1.element * sin(2 * .pi * frequency * Double($1.offset + 1) * time) } / harmonics.reduce(0, +)
                samples[first + i] = Float(wave * envelope * 0.8)
            }
        }
        activate()
        let engine = AVAudioEngine(), player = AVAudioPlayerNode()
        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: format)
        do {
            try engine.start()
            await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                player.scheduleBuffer(buffer, completionCallbackType: .dataPlayedBack) { _ in continuation.resume() }
                player.play()
            }
        } catch {
            // Nothing to play on: the notification still sounds
        }
        engine.stop()
        deactivate()
    }

    /// 3 pulses for a red alert, 5 for a critical one: a phone's vibration only comes one way
    static func vibrate(isCrimson: Bool) {
        (0..<(isCrimson ? 5 : 3)).forEach { pulse in
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(pulse) * 0.6) {
                AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
            }
        }
    }

    private func activate() {
        try? AVAudioSession.sharedInstance().setCategory(.playback, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private func deactivate() {
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
