import AudioToolbox
import Foundation

/// Whether the phone is in silent mode (the ring/silent switch, or the Action button), which iOS has
/// no API for. A system sound obeys it, unlike the speech: one made of silence "plays" for its whole
/// length with the ringer on, and ends at once in silent mode. A guess from timing, see
/// docs/alerts.md: nil when it can't tell.
enum SilentMode {
    /// The silent sound's length: with the ringer on, it ends this long after it starts
    private static let length = 0.3
    /// Ended sooner, it didn't play
    private static let silentBelow = 0.1
    /// Not ended by then, something else went wrong
    private static let timeout = 2.0

    /// A WAV file of [length] seconds of silence, written once
    private static let sound: URL? = {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("silent-mode-check.wav")
        let sampleRate = 8000
        let samples = Int(Double(sampleRate) * length)
        var data = Data()
        func append<T: FixedWidthInteger>(_ value: T) { withUnsafeBytes(of: value.littleEndian) { data.append(contentsOf: $0) } }
        data.append(contentsOf: Array("RIFF".utf8))
        append(UInt32(36 + samples * 2))
        data.append(contentsOf: Array("WAVEfmt ".utf8))
        // PCM, mono, 16 bits
        append(UInt32(16))
        append(UInt16(1))
        append(UInt16(1))
        append(UInt32(sampleRate))
        append(UInt32(sampleRate * 2))
        append(UInt16(2))
        append(UInt16(16))
        data.append(contentsOf: Array("data".utf8))
        append(UInt32(samples * 2))
        data.append(Data(count: samples * 2))
        return (try? data.write(to: url)).map { url }
    }()

    /// [completion] on the main queue: true in silent mode, false with the ringer on, nil when it
    /// can't tell, with how long the sound took to end
    static func check(_ completion: @escaping (Bool?, TimeInterval?) -> Void) {
        var id: SystemSoundID = 0
        guard let sound, AudioServicesCreateSystemSoundID(sound as CFURL, &id) == noErr else { return completion(nil, nil) }
        let start = Date()
        var isDone = false
        let finish: (Bool?, TimeInterval?) -> Void = { isSilent, elapsed in
            guard isDone == false else { return }
            isDone = true
            AudioServicesDisposeSystemSoundID(id)
            completion(isSilent, elapsed)
        }
        AudioServicesPlaySystemSoundWithCompletion(id) {
            let elapsed = Date().timeIntervalSince(start)
            DispatchQueue.main.async { finish(elapsed < silentBelow, elapsed) }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + timeout) { finish(nil, nil) }
    }
}
