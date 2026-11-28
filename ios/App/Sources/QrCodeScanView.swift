import AVFoundation
import PhotosUI
import SwiftUI
import TPMSCore
import Vision

/// What a scanned code leads to on the vehicle it was scanned for, once the camera has stopped
/// looking, like the Android app's QrCodeResultViewModel
struct QrCodeOutcome {
    let title: String
    let message: String
    /// What the confirming button says and binds, nil when there's nothing to assign
    var confirm: (title: String, sensors: [BoundSensor], calibrations: [SharedSensor])?
    var offersBluetooth = false

    static func of(_ result: QrCodeResult, vehicle: Vehicle, location: TyreLocation, monitor: Monitor) -> QrCodeOutcome {
        func place(_ sensor: BoundSensor) -> String {
            sensor.location == .single ? "wheel" : sensor.location.phrase
        }
        /// One of the vehicle's wheels has another sensor than the one [sensors] puts there
        func overwrites(_ sensors: [BoundSensor]) -> Bool {
            sensors.contains { sensor in
                monitor.sensors.contains { $0.vehicleId == vehicle.id && $0.location == sensor.location && $0.id != sensor.id }
            }
        }
        /// Where [sensor] assigned already, to another wheel or vehicle, is now
        func reassigned(_ sensor: BoundSensor) -> String? {
            monitor.sensors
                .first { $0.id == sensor.id && ($0.vehicleId != vehicle.id || $0.location != sensor.location) }
                .flatMap { current in
                    monitor.vehicles.first { $0.id == current.vehicleId }.map { "the \(place(current)) of \($0.name)" }
                }
        }
        func ask(_ sensors: [BoundSensor], _ question: String) -> QrCodeOutcome {
            let reassigns = sensors.lazy.compactMap(reassigned).first
            var message = question
            if let reassigns {
                message += "\n\n\(sensors.count == 1 ? "This sensor is" : "One of these sensors is") already assigned to \(reassigns), it will be reassigned."
            }
            if overwrites(sensors) { message += "\n\n⚠️ Warning: this will overwrite already assigned sensors" }
            return QrCodeOutcome(title: "Assign sensors", message: message, confirm: (reassigns != nil ? "Reassign" : "Yes", sensors, []))
        }

        switch result {
        case let .sensors(sensors):
            guard let placed = sensors.locations(on: vehicle.kind) else {
                return QrCodeOutcome(title: "Too many sensors", message: "This QR lists \(sensors.count) sensors, but \(vehicle.name) only needs \(vehicle.kind.locations.count).\nPlease assign via Bluetooth instead.")
            }
            // Assigning both would break the one sensor per wheel the database holds
            guard Set(placed.map(\.location)).count == placed.count else {
                return QrCodeOutcome(title: "Unreadable QR", message: "Error reading QR, please assign via Bluetooth instead")
            }
            return ask(
                placed.map { BoundSensor(id: $0.sensor.id, brand: .sysgration, vehicleId: vehicle.id, location: $0.location) },
                "Assign the \(placed.count) Sysgration sensors from this QR to \(vehicle.name)?"
            )

        case let .wicarlink(id):
            let sensor = BoundSensor(id: id, brand: .wicarlink, vehicleId: vehicle.id, location: location)
            return ask([sensor], "Assign the Wicarlink sensor \(id.asSensorId(.wicarlink)) to the \(place(sensor)) of \(vehicle.name)?")

        case let .shared(shared):
            // Each iPhone has its own identifier for a sensor without an ID, see isIdentifiedByAddress
            let usable = shared.sensors.filter { $0.brand.isIdentifiedByAddress == false }
            let noId = shared.sensors.count - usable.count
            let fitting = usable.filter { vehicle.kind.locations.contains($0.location) }
            let skipped = usable.filter { vehicle.kind.locations.contains($0.location) == false }
            let noIdNote = noId > 0 ? "\(noId == 1 ? "1 sensor carries" : "\(noId) sensors carry") no ID an iPhone can read (Pecham, Bekubee KY): assign \(noId == 1 ? "it" : "them") via Bluetooth." : nil
            guard fitting.isEmpty == false else {
                return QrCodeOutcome(
                    title: "Shared sensors",
                    message: noIdNote ?? "None of the sensors of \(shared.name) is on a wheel \(vehicle.name) has."
                )
            }
            let sensors = fitting.map { BoundSensor(id: $0.id, brand: $0.brand, vehicleId: vehicle.id, location: $0.location) }
            var message = "Assign the sensors of \(shared.name) to \(vehicle.name)?\n"
            for sensor in fitting {
                message += "\n· \(sensor.location.name): \(sensor.id.asSensorId(sensor.brand)) \(sensor.brand.name)\(sensor.adjusts ? ", calibrated" : "")"
            }
            let reassigns = sensors.compactMap { sensor in reassigned(sensor).map { (sensor, $0) } }
            for (sensor, place) in reassigns {
                message += "\n\n\(sensor.id.asSensorId(sensor.brand)) is assigned to \(place), it will be reassigned."
            }
            if overwrites(sensors) { message += "\n\n⚠️ Warning: this will overwrite already assigned sensors" }
            if skipped.isEmpty == false {
                message += "\n\nNot assigned, \(vehicle.name) has no \(skipped.map(\.location.phrase).joined(separator: " or "))."
            }
            if let noIdNote { message += "\n\n\(noIdNote)" }
            return QrCodeOutcome(title: "Shared sensors", message: message, confirm: (reassigns.isEmpty ? "Assign" : "Reassign", sensors, fitting))

        case .sharedByNewerApp:
            return QrCodeOutcome(title: "Shared sensors", message: "These sensors were shared by a newer version of the app, please update it to read them")

        case .sharedUnreadable:
            return QrCodeOutcome(title: "Shared sensors", message: "Error reading the shared sensors, please scan the code again")

        case let .duplicateWheelLocation(wheels):
            let list = wheels.map { "\n· \(TyreLocation.wheel($0).phrase)" }.joined()
            return QrCodeOutcome(title: "Inconsistent QR code", message: "This QR code has different sensors for the same wheel:\(list)", offersBluetooth: true)

        case .duplicateId:
            return QrCodeOutcome(title: "Inconsistent QR code", message: "This QR code has the same sensor ID several times.", offersBluetooth: true)
        }
    }
}

/// Scans a QR code, then asks what to do with what it holds. The first code found stops the search,
/// one which isn't a TPMS one only says so. The sensor ID can be typed in instead, or the code read
/// from pictures, the camera allowed or not.
struct QrCodeScanView: View {
    let vehicle: Vehicle
    let location: TyreLocation
    @ObservedObject var monitor: Monitor
    let scanBluetooth: () -> Void
    let onDone: () -> Void
    @State private var access = AVCaptureDevice.authorizationStatus(for: .video)
    @State private var isUnknownCode = false
    @State private var unknownCodeTask: Task<Void, Never>?
    @State private var outcome: QrCodeOutcome?
    @State private var isEnteringId = false
    @State private var typedId = ""
    @State private var pictures: [PhotosPickerItem] = []
    @State private var isReading = false
    @State private var choices: [QrCodeResult] = []
    @State private var noCodeInPictures = false
    @State private var printedIds = PrintedIdReadings()
    /// The printed ID is only offered once the code itself hasn't been read for a while
    @State private var isSuggestionDue = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if access == .authorized {
                QrCameraView(isActive: outcome == nil, onCode: read, onTexts: { printedIds.read(QrCodeResult.printedIds(in: $0)) })
                    .ignoresSafeArea()
                QrOverlay()
            } else if access == .notDetermined {
                ProgressView()
            } else {
                ContentUnavailableView {
                    Label("Camera not allowed", systemImage: "camera")
                } description: {
                    Text("Persistent TPMS needs the camera to scan the QR code. Allow it in Settings.")
                } actions: {
                    Button("Open Settings") {
                        URL(string: UIApplication.openSettingsURLString).map { UIApplication.shared.open($0) }
                    }
                }
                .environment(\.colorScheme, .dark)
            }
            VStack(spacing: 16) {
                Spacer()
                if access == .authorized {
                    Text(isUnknownCode ? "Not a known TPMS QR code" : "Point the camera at the sensors' QR code")
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        // The largest text sizes shrinking to fit below the frame
                        .minimumScaleFactor(0.3)
                        .lineLimit(4)
                    if isSuggestionDue, let id = printedIds.suggestion {
                        // The camera keeps looking for the code meanwhile
                        Button("Use the ID printed on the sensor: \(id)") { read(id) }
                            .buttonStyle(.borderedProminent)
                            .multilineTextAlignment(.center)
                    }
                }
                HStack(spacing: 12) {
                    Button("Enter ID") {
                        typedId = ""
                        isEnteringId = true
                    }
                    PhotosPicker("From gallery", selection: $pictures, matching: .images)
                }
                .buttonStyle(.bordered)
                .tint(.white)
                .disabled(isReading)
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 48)
            if isReading { ProgressView().tint(.white) }
        }
        .navigationTitle("Scan QR code")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            #if DEBUG
            if Demo.isOn {
                // No camera in the Simulator, only its overlay
                access = .authorized
                try? await Task.sleep(for: .seconds(1))
                Demo.scanned.map(read)
                return
            }
            #endif
            if access == .notDetermined {
                access = await AVCaptureDevice.requestAccess(for: .video) ? .authorized : .denied
            }
            try? await Task.sleep(for: .seconds(10))
            isSuggestionDue = true
        }
        .onChange(of: pictures) { _, items in
            guard items.isEmpty == false else { return }
            pictures = []
            readPictures(items)
        }
        .alert("Sensor ID", isPresented: $isEnteringId) {
            TextField("2221C5", text: $typedId)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .font(.body.monospaced())
                .onChange(of: typedId) { _, text in
                    let id = QrCodeResult.typedSensorId(text)
                    if id != text { typedId = id }
                }
            Button("Continue") { read(typedId) }
                .disabled(typedId.count < 6)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The 6 characters printed by the sensor's QR code, 0 to 9 and A to F")
        }
        .confirmationDialog("Several QR codes found", isPresented: Binding(get: { choices.isEmpty == false }, set: { if $0 == false { choices = [] } }), titleVisibility: .visible) {
            ForEach(choices, id: \.self) { result in
                Button(label(of: result)) { found(result) }
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("No TPMS QR code in the selected pictures", isPresented: $noCodeInPictures) {
            Button("OK", role: .cancel) {}
        }
        .alert(outcome?.title ?? "", isPresented: Binding(get: { outcome != nil }, set: { _ in }), presenting: outcome) { outcome in
            if let confirm = outcome.confirm {
                Button(confirm.title) {
                    monitor.assign(confirm.sensors, calibrations: confirm.calibrations)
                    onDone()
                }
                Button("Cancel", role: .cancel, action: onDone)
            } else if outcome.offersBluetooth {
                Button("Scan via Bluetooth", action: scanBluetooth)
                Button("OK", role: .cancel, action: onDone)
            } else {
                Button("OK", role: .cancel, action: onDone)
            }
        } message: { outcome in
            Text(outcome.message)
        }
    }

    private func found(_ result: QrCodeResult) {
        guard outcome == nil else { return }
        unknownCodeTask?.cancel()
        isUnknownCode = false
        outcome = .of(result, vehicle: vehicle, location: location, monitor: monitor)
    }

    private func label(of result: QrCodeResult) -> String {
        switch result {
        case let .wicarlink(id): "Wicarlink sensor \(id.asSensorId(.wicarlink))"
        case let .sensors(sensors): "\(sensors.count) Sysgration sensors"
        case let .shared(shared): "Sensors of \(shared.name)"
        default: "A QR code that can't be used"
        }
    }

    /// The code found in the pictures, or the choice of them when several are
    private func readPictures(_ items: [PhotosPickerItem]) {
        isReading = true
        Task {
            var pictures = [(codes: [String], texts: [String])]()
            for item in items {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    pictures.append(await Task.detached { Self.content(of: data) }.value)
                }
            }
            isReading = false
            let results = QrCodeResult.parse(pictures: pictures)
            switch results.count {
            case 0: noCodeInPictures = true
            case 1: found(results[0])
            default: choices = results
            }
        }
    }

    /// The QR codes and the text Vision finds in a picture, turned upright from its EXIF orientation
    private nonisolated static func content(of data: Data) -> (codes: [String], texts: [String]) {
        let codes = VNDetectBarcodesRequest()
        codes.symbologies = [.qr]
        let texts = VNRecognizeTextRequest.printedIds()
        try? VNImageRequestHandler(data: data).perform([codes, texts])
        return ((codes.results ?? []).compactMap(\.payloadStringValue).filter { $0.isEmpty == false }, texts.lines)
    }

    private func read(_ code: String) {
        guard outcome == nil else { return }
        if let result = QrCodeResult.parse(code) {
            found(result)
        } else {
            // The camera reports a code at every frame it's in: the text stays while it's in view
            isUnknownCode = true
            unknownCodeTask?.cancel()
            unknownCodeTask = Task { @MainActor in
                try? await Task.sleep(for: .seconds(2))
                if Task.isCancelled == false { isUnknownCode = false }
            }
        }
    }
}

/// The usual scanner look: the preview dimmed around a clear square, its corners marked
private struct QrOverlay: View {
    var body: some View {
        GeometryReader { geometry in
            let side = min(geometry.size.width, geometry.size.height) * 0.7
            let square = CGRect(x: (geometry.size.width - side) / 2, y: (geometry.size.height - side) / 2, width: side, height: side)
            Path { path in
                path.addRect(CGRect(origin: .zero, size: geometry.size))
                path.addRoundedRect(in: square, cornerSize: CGSize(width: 4, height: 4))
            }
            .fill(Color.black.opacity(0.55), style: FillStyle(eoFill: true))
            Path { path in
                let arm = side * 0.15
                for (corner, x, y) in [
                    (CGPoint(x: square.minX, y: square.minY), CGFloat(1), CGFloat(1)),
                    (CGPoint(x: square.maxX, y: square.minY), -1, 1),
                    (CGPoint(x: square.minX, y: square.maxY), 1, -1),
                    (CGPoint(x: square.maxX, y: square.maxY), -1, -1),
                ] {
                    path.move(to: CGPoint(x: corner.x + arm * x, y: corner.y))
                    path.addLine(to: corner)
                    path.addLine(to: CGPoint(x: corner.x, y: corner.y + arm * y))
                }
            }
            .stroke(.white, style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}

private extension VNRecognizeTextRequest {
    /// Reads a sensor ID as printed, rather than correcting it into a word
    static func printedIds() -> VNRecognizeTextRequest {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        return request
    }

    /// The best reading of each line, then of its words: a printed ID can be a word of a longer line,
    /// or split into several words
    var lines: [String] {
        (results ?? [])
            .compactMap { $0.topCandidates(1).first?.string }
            .flatMap { line in [line] + line.split(separator: " ").map(String.init) }
    }
}

/// The back camera's preview, reporting every QR code it sees, and the text of a frame every half
/// second: reading text takes longer, the codes are still looked for in every frame
private struct QrCameraView: UIViewRepresentable {
    let isActive: Bool
    let onCode: (String) -> Void
    let onTexts: ([String]) -> Void

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate, AVCaptureVideoDataOutputSampleBufferDelegate, @unchecked Sendable {
        let session = AVCaptureSession()
        private let queue = DispatchQueue(label: "qr-camera")
        private let textQueue = DispatchQueue(label: "qr-camera-text")
        /// Only touched on textQueue
        private var textReadAt: CMTime?
        var onCode: (String) -> Void = { _ in }
        var onTexts: ([String]) -> Void = { _ in }

        override init() {
            super.init()
            // The small code etched on a Wicarlink cap needs the resolution
            if session.canSetSessionPreset(.hd1920x1080) { session.sessionPreset = .hd1920x1080 }
            let output = AVCaptureMetadataOutput()
            guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  let input = try? AVCaptureDeviceInput(device: camera),
                  session.canAddInput(input), session.canAddOutput(output)
            else { return }
            session.addInput(input)
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.qr]
            let frames = AVCaptureVideoDataOutput()
            frames.alwaysDiscardsLateVideoFrames = true
            if session.canAddOutput(frames) {
                session.addOutput(frames)
                frames.setSampleBufferDelegate(self, queue: textQueue)
            }
        }

        func setRunning(_ running: Bool) {
            queue.async { [session] in
                if running, session.isRunning == false { session.startRunning() }
                if running == false, session.isRunning { session.stopRunning() }
            }
        }

        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject], from connection: AVCaptureConnection) {
            objects
                .compactMap { ($0 as? AVMetadataMachineReadableCodeObject)?.stringValue }
                .forEach(onCode)
        }

        func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
            let time = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
            if let textReadAt, CMTimeGetSeconds(CMTimeSubtract(time, textReadAt)) < 0.5 { return }
            textReadAt = time
            let request = VNRecognizeTextRequest.printedIds()
            // The back camera's frames come sideways in portrait
            try? VNImageRequestHandler(cmSampleBuffer: sampleBuffer, orientation: .right).perform([request])
            let lines = request.lines
            guard lines.isEmpty == false else { return }
            DispatchQueue.main.async { [weak self] in self?.onTexts(lines) }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = context.coordinator.session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {
        context.coordinator.onCode = onCode
        context.coordinator.onTexts = onTexts
        context.coordinator.setRunning(isActive)
    }

    static func dismantleUIView(_ view: PreviewView, coordinator: Coordinator) {
        coordinator.setRunning(false)
    }
}
