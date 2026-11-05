import AVFoundation
import SwiftUI
import TPMSCore

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
            sensor.location == .single ? tr("wheel") : sensor.location.phrase
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
                    monitor.vehicles.first { $0.id == current.vehicleId }.map { tr("the \(place(current)) of \($0.name)") }
                }
        }
        func ask(_ sensors: [BoundSensor], _ question: String) -> QrCodeOutcome {
            let reassigns = sensors.lazy.compactMap(reassigned).first
            var message = question
            if let reassigns {
                message += "\n\n" + (sensors.count == 1 ? tr("This sensor is already assigned to \(reassigns), it will be reassigned.") : tr("One of these sensors is already assigned to \(reassigns), it will be reassigned."))
            }
            if overwrites(sensors) { message += tr("\n\nThis will overwrite already assigned sensors.") }
            return QrCodeOutcome(title: tr("Assign sensors"), message: message, confirm: (reassigns != nil ? tr("Reassign") : tr("Yes"), sensors, []))
        }

        switch result {
        case let .sensors(sensors):
            guard let placed = sensors.locations(on: vehicle.kind) else {
                return QrCodeOutcome(title: tr("Too many sensors"), message: tr("This QR lists \(sensors.count) sensors, but \(vehicle.name) only needs \(vehicle.kind.locations.count).\nPlease assign via Bluetooth instead."))
            }
            // Assigning both would break the one sensor per wheel the database holds
            guard Set(placed.map(\.location)).count == placed.count else {
                return QrCodeOutcome(title: tr("Unreadable QR"), message: tr("Error reading QR, please assign via Bluetooth instead"))
            }
            return ask(
                placed.map { BoundSensor(id: $0.sensor.id, brand: .sysgration, vehicleId: vehicle.id, location: $0.location) },
                tr("Assign the \(placed.count) Sysgration sensors from this QR to \(vehicle.name)?")
            )

        case let .wicarlink(id):
            let sensor = BoundSensor(id: id, brand: .wicarlink, vehicleId: vehicle.id, location: location)
            return ask([sensor], tr("Assign the Wicarlink sensor \(id.asSensorId(.wicarlink)) to the \(place(sensor)) of \(vehicle.name)?"))

        case let .shared(shared):
            // Each iPhone has its own identifier for a sensor without an ID, see isIdentifiedByAddress
            let usable = shared.sensors.filter { $0.brand.isIdentifiedByAddress == false }
            let noId = shared.sensors.count - usable.count
            let fitting = usable.filter { vehicle.kind.locations.contains($0.location) }
            let skipped = usable.filter { vehicle.kind.locations.contains($0.location) == false }
            let noIdNote = noId == 1 ? tr("1 sensor carries no ID an iPhone can read (Pecham, Bekubee KY): assign it via Bluetooth.") : noId > 1 ? tr("\(noId) sensors carry no ID an iPhone can read (Pecham, Bekubee KY): assign them via Bluetooth.") : nil
            guard fitting.isEmpty == false else {
                return QrCodeOutcome(
                    title: tr("Shared sensors"),
                    message: noIdNote ?? tr("None of the sensors of \(shared.name) is on a wheel \(vehicle.name) has.")
                )
            }
            let sensors = fitting.map { BoundSensor(id: $0.id, brand: $0.brand, vehicleId: vehicle.id, location: $0.location) }
            var message = tr("Assign the sensors of \(shared.name) to \(vehicle.name)?\n")
            for sensor in fitting {
                message += "\n· \(sensor.location.name): \(sensor.id.asSensorId(sensor.brand)) \(sensor.brand.name)" + (sensor.adjusts ? tr(", calibrated") : "")
            }
            let reassigns = sensors.compactMap { sensor in reassigned(sensor).map { (sensor, $0) } }
            for (sensor, place) in reassigns {
                message += tr("\n\n\(sensor.id.asSensorId(sensor.brand)) is assigned to \(place), it will be reassigned.")
            }
            if overwrites(sensors) { message += tr("\n\nThis will overwrite already assigned sensors.") }
            if skipped.isEmpty == false {
                message += tr("\n\nNot assigned, \(vehicle.name) has no \(skipped.map(\.location.phrase).joined(separator: tr(" or "))).")
            }
            if let noIdNote { message += "\n\n\(noIdNote)" }
            return QrCodeOutcome(title: tr("Shared sensors"), message: message, confirm: (reassigns.isEmpty ? tr("Assign") : tr("Reassign"), sensors, fitting))

        case .sharedByNewerApp:
            return QrCodeOutcome(title: tr("Shared sensors"), message: tr("These sensors were shared by a newer version of the app, please update it to read them"))

        case .sharedUnreadable:
            return QrCodeOutcome(title: tr("Shared sensors"), message: tr("Error reading the shared sensors, please scan the code again"))

        case let .duplicateWheelLocation(wheels):
            let list = wheels.map { "\n· \(TyreLocation.wheel($0).phrase)" }.joined()
            return QrCodeOutcome(title: tr("Inconsistent QR code"), message: tr("This QR code has different sensors for the same wheel:\(list)"), offersBluetooth: true)

        case .duplicateId:
            return QrCodeOutcome(title: tr("Inconsistent QR code"), message: tr("This QR code has the same sensor ID several times."), offersBluetooth: true)
        }
    }
}

/// Scans a QR code, then asks what to do with what it holds. The first code found stops the search,
/// one which isn't a TPMS one only says so.
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

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if access == .authorized {
                QrCameraView(isActive: outcome == nil, onCode: read)
                    .ignoresSafeArea()
                QrOverlay(text: isUnknownCode ? tr("Not a known TPMS QR code") : tr("Point the camera at the sensors' QR code"))
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

    private func read(_ code: String) {
        guard outcome == nil else { return }
        if let result = QrCodeResult.parse(code) {
            unknownCodeTask?.cancel()
            outcome = .of(result, vehicle: vehicle, location: location, monitor: monitor)
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
    let text: String

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
            Text(text)
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
                .frame(maxWidth: .infinity)
                .position(x: geometry.size.width / 2, y: square.maxY + (geometry.size.height - square.maxY) / 2)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}

/// The back camera's preview, reporting every QR code it sees
private struct QrCameraView: UIViewRepresentable {
    let isActive: Bool
    let onCode: (String) -> Void

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        let session = AVCaptureSession()
        private let queue = DispatchQueue(label: "qr-camera")
        var onCode: (String) -> Void = { _ in }

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
        context.coordinator.setRunning(isActive)
    }

    static func dismantleUIView(_ view: PreviewView, coordinator: Coordinator) {
        coordinator.setRunning(false)
    }
}
