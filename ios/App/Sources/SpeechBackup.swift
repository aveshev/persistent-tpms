import AVFoundation
import Foundation
import TPMSCore
import UserNotifications

/// What AlertSpeaker's loops would say, as a notification repeating on its own, see docs/alerts.md
/// ("When the app can't speak"). The loops only go on while the app runs, which in the background is
/// when a sensor is heard: out of range, after the ride, or with the app swiped away, nothing would
/// be said. Its sound is the loops' own, the phrase spoken or the tones, recorded into Library/Sounds,
/// which iOS plays without the app. The loops push it back after each phrase they say, so it only
/// sounds when they couldn't.
///
/// One notification for every alert, as the loops say them all in one phrase: repeating, each one
/// replaces the one before rather than piling up.
final class SpeechBackup {
    static let identifier = "alertSpeechBackup"
    static let category = "alertSpeechBackup"

    /// The critical loop's: every minute, the shortest a repeating notification can do, while it says
    /// them every 20 seconds
    static let crimsonPeriod: TimeInterval = 60
    /// The reminders' 10 minutes, plus a margin: due a little after the loop would say it, never
    /// together with it
    static let reminderPeriod: TimeInterval = 10 * 60 + 30

    struct Plan: Equatable {
        /// As the loop says it, twice
        let text: String
        let isCrimson: Bool
        let period: TimeInterval
        let sound: Settings.AlertSound
    }

    private let center = UNUserNotificationCenter.current()
    /// Each schedule's, a recording finished after a newer one was asked for is dropped
    private var generation = 0
    /// Kept until they're done
    private var recorders = [SpeechRecorder]()

    /// Replaces the one scheduled, once its sound is recorded: the one before goes on meanwhile
    func schedule(_ plan: Plan) {
        generation += 1
        let generation = generation
        record(plan) { [weak self] name in
            guard let self, self.generation == generation else { return }
            let content = UNMutableNotificationContent()
            content.title = plan.isCrimson ? tr("Critical tyre alert") : tr("Tyre alert")
            content.body = plan.text.components(separatedBy: ". ").first.map { $0.hasSuffix(".") ? $0 : $0 + "." } ?? plan.text
            // Its own sound, which a silent recording turns into the vibration alone
            content.sound = name.map { UNNotificationSound(named: UNNotificationSoundName($0)) } ?? .default
            content.interruptionLevel = .timeSensitive
            content.categoryIdentifier = Self.category
            // Counted from now, when the loop last said it or is about to: a repeating one can't
            // start at any other time
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: plan.period, repeats: true)
            center.add(UNNotificationRequest(identifier: Self.identifier, content: content, trigger: trigger))
        }
    }

    /// Nothing left for the loops to say: what it said last goes too
    func cancel() {
        generation += 1
        center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
        center.removeDeliveredNotifications(withIdentifiers: [Self.identifier])
    }

    /// When it last sounded, as it shows among the delivered notifications
    func lastSounded() async -> Date? {
        await center.deliveredNotifications().first { $0.request.identifier == Self.identifier }?.date
    }

    // MARK: Sound

    private static var sounds: URL? {
        FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first?.appendingPathComponent("Sounds")
    }

    /// The file's name in Library/Sounds, recorded once for each phrase and sound, nil when it can't
    private func record(_ plan: Plan, _ completion: @escaping (String?) -> Void) {
        guard let sounds = Self.sounds else { return completion(nil) }
        let kind = switch plan.sound {
        case .speech: "speech"
        case .tones: plan.isCrimson ? "tones-crimson" : "tones-red"
        case .none: "silence"
        }
        let name = "backup-\(kind)-\(Self.hash(plan.sound == .speech ? plan.text : "")).caf"
        let url = sounds.appendingPathComponent(name)
        guard FileManager.default.fileExists(atPath: url.path) == false else { return completion(name) }
        try? FileManager.default.createDirectory(at: sounds, withIntermediateDirectories: true)
        // Written aside then moved, a sound half written is never played. Aside under a name of its
        // own: two recordings of the same phrase may go on at once.
        let temporary = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString)-\(name)")
        let finish: (Bool) -> Void = { isWritten in
            // The other one may have got there first
            let isMoved = isWritten && ((try? FileManager.default.moveItem(at: temporary, to: url)) != nil || FileManager.default.fileExists(atPath: url.path))
            try? FileManager.default.removeItem(at: temporary)
            completion(isMoved ? name : nil)
        }
        switch plan.sound {
        case .speech:
            speak(plan.text, into: temporary, finish)
        case .tones:
            finish(AlertTones.buffer(isCrimson: plan.isCrimson).map { Self.write([$0], to: temporary) } ?? false)
        case .none:
            // A second of silence: vibrates as a notification's sound does
            let silence = AVAudioFormat(standardFormatWithSampleRate: 8000, channels: 1).flatMap { AVAudioPCMBuffer(pcmFormat: $0, frameCapacity: 8000) }
            silence?.frameLength = 8000
            finish(silence.map { Self.write([$0], to: temporary) } ?? false)
        }
    }

    private func speak(_ text: String, into url: URL, _ finish: @escaping (Bool) -> Void) {
        let recorder = SpeechRecorder(url: url)
        recorders.append(recorder)
        recorder.record(text) { [weak self, weak recorder] isWritten in
            self?.recorders.removeAll { $0 === recorder }
            finish(isWritten)
        }
    }

    /// 16-bit linear PCM, which a notification's sound can be
    static func settings(_ format: AVAudioFormat) -> [String: Any] {
        [
            AVFormatIDKey: kAudioFormatLinearPCM,
            AVSampleRateKey: format.sampleRate,
            AVNumberOfChannelsKey: format.channelCount,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false,
        ]
    }

    private static func write(_ buffers: [AVAudioPCMBuffer], to url: URL) -> Bool {
        guard let format = buffers.first?.format else { return false }
        try? FileManager.default.removeItem(at: url)
        do {
            let file = try AVAudioFile(forWriting: url, settings: settings(format), commonFormat: format.commonFormat, interleaved: format.isInterleaved)
            try buffers.forEach { try file.write(from: $0) }
            return true
        } catch {
            return false
        }
    }

    /// Stable across launches, unlike hashValue: names the recording of a phrase
    private static func hash(_ text: String) -> String {
        String(text.utf8.reduce(UInt64(14_695_981_039_346_656_037)) { ($0 ^ UInt64($1)) &* 1_099_511_628_211 }, radix: 16)
    }
}

/// Records a phrase as the speech says it, the same voice, with a synthesizer of its own: another
/// recording going on meanwhile can't write into its file. The synthesizer hands it over in buffers,
/// an empty one or its delegate telling it's over.
private final class SpeechRecorder: NSObject, AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private let url: URL
    private var file: AVAudioFile?
    private var completion: ((Bool) -> Void)?

    init(url: URL) {
        self.url = url
        super.init()
        synthesizer.delegate = self
    }

    /// [completion] on the main queue, with whether something was written
    func record(_ text: String, _ completion: @escaping (Bool) -> Void) {
        self.completion = completion
        try? FileManager.default.removeItem(at: url)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = .alerts
        synthesizer.write(utterance) { [weak self] buffer in
            guard let buffer = buffer as? AVAudioPCMBuffer else { return }
            // Its callbacks may come on another queue
            DispatchQueue.main.async { self?.received(buffer) }
        }
    }

    private func received(_ buffer: AVAudioPCMBuffer) {
        guard buffer.frameLength > 0 else { return finish() }
        if file == nil {
            file = try? AVAudioFile(forWriting: url, settings: SpeechBackup.settings(buffer.format), commonFormat: buffer.format.commonFormat, interleaved: buffer.format.isInterleaved)
        }
        try? file?.write(from: buffer)
    }

    private func finish() {
        guard let completion else { return }
        self.completion = nil
        let isWritten = file != nil
        // Closes it
        file = nil
        completion(isWritten)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        // After the buffers already handed over
        DispatchQueue.main.async { self.finish() }
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { self.finish() }
    }
}
