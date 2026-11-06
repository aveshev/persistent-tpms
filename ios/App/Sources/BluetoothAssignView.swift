import Combine
import CoreBluetooth
import SwiftUI
import TPMSCore

/// Assigns [location] of [vehicle] a sensor found by Bluetooth, see AssignStep. Sensors already
/// bound to this vehicle are ignored. Like the Android app's BluetoothAssignViewModel.
final class BluetoothAssignModel: ObservableObject {
    /// Asked before going on: on the page itself, but boundElsewhere in an alert
    enum Question: Equatable {
        /// A single sensor was found on a wheel: assign it, or make sure by taking it off and back
        case oneFound
        /// Several were, taking it off and back tells which one
        case manyFound(Int)
        /// The sensor found belongs to this vehicle, assigning it here takes it from there
        case boundElsewhere(Vehicle)
    }

    let vehicle: Vehicle
    let location: TyreLocation
    private let monitor: Monitor
    private var subscription: AnyCancellable?
    private var lowPressureTask: Task<Void, Never>?
    /// The last reading of each sensor heard, to bind the one found with it
    private var heard = [Int: SensorReading]()
    private var readings = [Int: Float]()

    @Published private(set) var step = AssignStep.ask {
        didSet { stepChanged(from: oldValue) }
    }
    @Published private(set) var question: Question?
    /// Nothing was found on a wheel for a while: maybe the tyre is nearly flat
    @Published private(set) var offersLowPressure = false
    /// The sensor found was assigned, the flow is done
    @Published private(set) var assigned: SensorReading?

    private static let lowPressureAfter: Duration = .seconds(10)

    init(vehicle: Vehicle, location: TyreLocation, monitor: Monitor) {
        self.vehicle = vehicle
        self.location = location
        self.monitor = monitor
        subscription = monitor.heard.sink { [weak self] in self?.onReading($0) }
    }

    deinit { lowPressureTask?.cancel() }

    private func onReading(_ reading: SensorReading) {
        guard step.isListening, monitor.sensors.contains(where: { $0.id == reading.sensorId && $0.vehicleId == vehicle.id }) == false else { return }
        let kpa = reading.pressure.kpa
        let next = step.after(sensorId: reading.sensorId, kpa: kpa, readings: readings)
        readings[reading.sensorId] = kpa
        heard[reading.sensorId] = reading
        step = next
        if case let .found(sensorId) = next { found(sensorId) }
    }

    private func stepChanged(from previous: AssignStep) {
        // Nothing on a wheel 10 s after it should be: offers the sensors heard below it
        if case .putOn = previous, case .putOn = step { return }
        lowPressureTask?.cancel()
        offersLowPressure = false
        if case .putOn = step {
            lowPressureTask = Task { @MainActor [weak self] in
                try? await Task.sleep(for: Self.lowPressureAfter)
                if Task.isCancelled == false { self?.offersLowPressure = true }
            }
        }
        #if DEBUG
        simulateDemo()
        #endif
    }

    func answer(isOnWheel: Bool) {
        step = isOnWheel ? .takeOff(candidates: nil) : .putOn()
    }

    /// Done with the sensor on or off the wheel
    func next() {
        switch step {
        case let .putOn(found, _): question = found.count == 1 ? .oneFound : .manyFound(found.count)
        case let .takeOff(_, off): step = .putBack(candidates: off)
        default: break
        }
    }

    /// The sensors heard below the on-wheel pressure are taken off the wheel instead
    func lowPressure() {
        if case let .putOn(_, low) = step { step = .takeOff(candidates: low) }
    }

    /// The single sensor found on a wheel is assigned without taking it off and back
    func assignFound() {
        guard case let .putOn(found, _) = step, let sensorId = found.first else { return }
        question = nil
        self.found(sensorId)
    }

    /// Takes the sensors found on a wheel off it, to tell which one is the user's
    func checkAgain() {
        guard case let .putOn(found, _) = step else { return }
        question = nil
        step = .takeOff(candidates: found)
    }

    /// Takes the sensor from the vehicle it was bound to
    func confirmBoundElsewhere() {
        if case let .found(sensorId) = step { bind(sensorId) }
    }

    /// Starts listening again rather than assigning the sensor of another vehicle
    func cancelBoundElsewhere() {
        question = nil
        step = .ask
    }

    private func found(_ sensorId: Int) {
        step = .found(sensorId: sensorId)
        let elsewhere = monitor.sensors
            .first { $0.id == sensorId && $0.vehicleId != vehicle.id }
            .flatMap { sensor in monitor.vehicles.first { $0.id == sensor.vehicleId } }
        if let elsewhere { question = .boundElsewhere(elsewhere) } else { bind(sensorId) }
    }

    private func bind(_ sensorId: Int) {
        guard let reading = heard[sensorId] else { return }
        monitor.assign(reading, to: vehicle, at: location)
        question = nil
        assigned = reading
    }

    #if DEBUG
    /// The Simulator has no Bluetooth: in the demo, sensors follow what each step asks. 1 is the
    /// user's, 2 sits on a wheel nearby.
    private func simulateDemo() {
        guard Demo.isOn else { return }
        let packets: [(Int, Float)] = switch step {
        case .putOn: [(1, 230), (2, 250)]
        case .takeOff: [(2, 251), (1, 2)]
        case .putBack: [(2, 252), (1, 228)]
        case .ask, .found: []
        }
        let monitor = monitor
        Task { @MainActor in
            for (sensorId, kpa) in packets {
                try? await Task.sleep(for: .seconds(1))
                Demo.reading(sensorId: sensorId, kpa: kpa).map(monitor.simulate)
            }
        }
    }
    #endif
}

/// The user puts the sensor on the wheel, takes it off and puts it back, see AssignStep: the step's
/// instruction in the middle, its actions anchored at the bottom
struct BluetoothAssignView: View {
    @StateObject private var model: BluetoothAssignModel
    @ObservedObject private var monitor: Monitor
    private let onDone: () -> Void
    private static let forcesUpdate = "This will force it to send an update"

    init(vehicle: Vehicle, location: TyreLocation, monitor: Monitor, onDone: @escaping () -> Void) {
        _model = StateObject(wrappedValue: BluetoothAssignModel(vehicle: vehicle, location: location, monitor: monitor))
        self.monitor = monitor
        self.onDone = onDone
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 16) {
                    if monitor.bluetooth != .poweredOn {
                        Banner(text: "Bluetooth is off or not allowed: the sensors can't be heard.", systemImage: "antenna.radiowaves.left.and.right.slash", color: .orange)
                    }
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .font(.system(size: 56))
                        .foregroundStyle(Theme.primary)
                    instructions
                }
                .multilineTextAlignment(.center)
                .padding(24)
                .frame(maxWidth: .infinity)
            }
            .defaultScrollAnchor(.center)
            Divider()
            VStack(spacing: 8) { actions }
                .buttonBorderShape(.capsule)
                .controlSize(.large)
                .padding(16)
        }
        .navigationTitle("Scan via Bluetooth")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(model.assigned != nil)
        // The user walks to the wheel and back, and iOS hears a sensor far less often once locked
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
        .alert("Already assigned", isPresented: Binding(
            get: { boundElsewhere != nil },
            set: { if $0 == false, boundElsewhere != nil { model.cancelBoundElsewhere() } }
        )) {
            Button("Reassign") { model.confirmBoundElsewhere() }
            Button("Cancel", role: .cancel) { model.cancelBoundElsewhere() }
        } message: {
            Text("This sensor is already assigned to \(boundElsewhere?.name ?? ""). Reassign it to \(model.vehicle.name)?")
        }
    }

    private var boundElsewhere: Vehicle? {
        if case let .boundElsewhere(vehicle) = model.question { vehicle } else { nil }
    }

    /// "the front left wheel on My car", a monowheel's only wheel is just "the wheel"
    private var wheelOn: Text {
        let wheel = model.location == .single
            ? Text("the wheel")
            : Text("the ") + Text(model.location.shortPhrase).bold() + Text(" wheel")
        return wheel + Text(" on ") + Text(model.vehicle.name).bold()
    }

    @ViewBuilder private var instructions: some View {
        if let assigned = model.assigned {
            // A sensor without an ID has nothing to show, see SensorBrand.isIdentifiedByAddress
            let sensor = assigned.brand.isIdentifiedByAddress ? "Sensor" : "Sensor \(assigned.sensorId.asSensorId(assigned.brand))"
            (Text("\(sensor) assigned to ") + wheelOn)
                .font(.title2.weight(.semibold))
        } else {
            (Text("Assigning to ") + wheelOn)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            switch (model.question, model.step) {
            case (.oneFound?, _):
                instruction("One sensor found", "Assign it to the \(model.location.phrase), or check again to make sure?")
            case let (.manyFound(count)?, _):
                instruction("\(count) on-wheel sensors found", "Let's figure out which one to assign")
            case (_, .ask):
                instruction("Is the sensor you want to assign on the wheel right now?")
            case let (_, .putOn(found, _)):
                instruction("Put the sensor on the wheel", Self.forcesUpdate)
                listening("Listening for on-wheel sensors…", found.count, "on-wheel")
            case let (_, .takeOff(_, off)):
                instruction("Remove the sensor from the wheel", Self.forcesUpdate)
                listening("Listening for off-wheel sensors…", off.count, "off-wheel")
            case (_, .putBack):
                instruction("Put the sensor back on the wheel", Self.forcesUpdate)
                listening("Listening for the sensor to come back on…", 0, "")
            case (_, .found):
                instruction("Sensor found")
            }
        }
    }

    private func instruction(_ text: String, _ detail: String? = nil) -> some View {
        VStack(spacing: 8) {
            Text(text).font(.title2.weight(.semibold))
            if let detail { Text(detail).font(.subheadline).foregroundStyle(.secondary) }
        }
    }

    private func listening(_ text: String, _ count: Int, _ kind: String) -> some View {
        VStack(spacing: 8) {
            ProgressView()
            Text(text).font(.subheadline).foregroundStyle(.secondary)
            // Its line kept while nothing is found yet, so the page doesn't move when something is
            Text(count > 0 ? "\(count) \(kind) sensor\(count > 1 ? "s" : "") found" : " ").font(.headline)
        }
    }

    @ViewBuilder private var actions: some View {
        if model.assigned != nil {
            primary("Done", action: onDone)
        } else if model.question == .oneFound {
            secondary("Check again", action: model.checkAgain)
            primary("Assign", action: model.assignFound)
        } else if case .manyFound = model.question {
            primary("Continue", action: model.checkAgain)
        } else {
            switch model.step {
            case .ask:
                secondary("No, not yet") { model.answer(isOnWheel: false) }
                primary("Yes, it's on the wheel") { model.answer(isOnWheel: true) }
            case let .putOn(found, low):
                // Below 10 kPa, a sensor isn't told on a wheel from in the hand
                if model.offersLowPressure, found.isEmpty, low.isEmpty == false {
                    Button("Low pressure tyre?", action: model.lowPressure)
                }
                primary("Continue", action: model.next).disabled(found.isEmpty)
            case let .takeOff(_, off):
                primary("Continue", action: model.next).disabled(off.isEmpty)
            // Assigned by itself once back on the wheel
            case .putBack, .found:
                primary("Continue") {}.disabled(true)
            }
        }
    }

    private func primary(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { Text(title).frame(maxWidth: .infinity) }
            .buttonStyle(.borderedProminent)
    }

    private func secondary(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { Text(title).frame(maxWidth: .infinity) }
            .buttonStyle(.bordered)
    }
}
