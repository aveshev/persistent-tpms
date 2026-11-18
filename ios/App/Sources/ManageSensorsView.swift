import SwiftUI
import TPMSCore

/// A sensor coming from [location] of another vehicle, see ManageSensorsView
struct IncomingSensor {
    let location: TyreLocation
    let move: (_ target: TyreLocation, _ swap: Bool) -> Void
}

/// The vehicle's sensors, laid out like the main screen: every location is outlined, tapping one
/// manages its sensor, or assigns it one. Moving a sensor picks where it goes on the vehicle, see
/// MoveChain, or on another vehicle, whose page this is then: with [incoming], the user taps the
/// wheel the sensor goes to. Like the Android app's ManageSensors.
struct ManageSensorsView: View {
    let vehicleId: UUID
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    var incoming: IncomingSensor?
    /// Waiting for the user to pick where the last sensor of the chain goes
    @State private var moving: MoveChain?
    /// Its last location has a sensor too: just swap them, or continue the chain
    @State private var asking: MoveChain?
    /// Ready, its arrows shown until the user applies or cancels it
    @State private var confirming: MoveChain?
    @State private var assigning: TyreLocation?
    @State private var managing: TyreLocation?
    /// The incoming sensor's wheel has one: swap them, or replace it
    @State private var askingIncoming: TyreLocation?
    /// A sensor going to a wheel of another vehicle, picked on its page
    @State private var movingToVehicle: VehicleMove?
    /// Going to a monowheel's only wheel, which has a sensor: swap them, or replace it
    @State private var askingMonowheel: VehicleMove?
    @State private var calibrating: BoundSensor?
    @State private var message: String?
    @Environment(\.dismiss) private var dismiss

    private static let managePrompt = tr("Tap the wheel/sensor to manage")
    private static let movePrompt = tr("Tap the wheel to move to")
    /// The most the actions show: always this tall, so nothing moves when they change
    private static let actionsHeight: CGFloat = 104
    private static let sideMaxShare: CGFloat = 0.35
    private static let sideMaxWidth: CGFloat = 260

    struct VehicleMove: Identifiable, Hashable {
        let location: TyreLocation
        let vehicle: Vehicle
        var id: UUID { vehicle.id }
    }

    var body: some View {
        Group {
            if let vehicle = monitor.vehicles.first(where: { $0.id == vehicleId }) { page(vehicle) }
        }
        .navigationTitle(incoming != nil ? tr("Move to") : tr("Manage sensors"))
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(moving != nil || confirming != nil)
    }

    private func page(_ vehicle: Vehicle) -> some View {
        let all = vehicle.kind.locations
        let occupied = Set(monitor.sensors.filter { $0.vehicleId == vehicle.id }.map(\.location))
        let name = Text(vehicle.name)
            .font(.title3)
            .lineLimit(1)
            .padding(.horizontal, 16)
            .padding(.top, 8)
        let vehicleView =
            VehicleView(
                vehicle: vehicle,
                monitor: monitor,
                settings: settings,
                isManaging: true,
                move: move(all: all, occupied: occupied),
                arrows: confirming?.moves(occupied: occupied) ?? (asking ?? moving)?.picked ?? [],
                onSensor: { location in
                    if occupied.contains(location) { managing = location } else { assigning = location }
                }
            )
            .padding(.horizontal, 4)
            .padding(.vertical, 8)
            // Nothing to tap until it's applied or cancelled
            .overlay { if confirming != nil { Color.clear.contentShape(Rectangle()).onTapGesture {} } }
            .overlay(alignment: .bottom) {
                if let message {
                    Text(message)
                        .font(.callout)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(.regularMaterial, in: Capsule())
                        .padding(8)
                        .transition(.opacity)
                }
            }
        return GeometryReader { geometry in
            // The vehicle needs all the height when wider than tall: what's over and under it goes to
            // its side, like Android's VehicleBeside
            if geometry.size.width > geometry.size.height {
                HStack(spacing: 0) {
                    VStack(spacing: 0) {
                        name
                        actions(vehicle).padding(16)
                    }
                    .frame(width: min(geometry.size.width * Self.sideMaxShare, Self.sideMaxWidth))
                    Divider()
                    vehicleView
                }
            } else {
                VStack(spacing: 0) {
                    name
                    vehicleView
                    Divider()
                    actions(vehicle)
                        .frame(height: Self.actionsHeight)
                        .padding(16)
                }
            }
        }
        .background(Theme.background)
        // Here, rather than on the message: its page is still coming back when it's set
        .task(id: message) {
            guard message != nil else { return }
            try? await Task.sleep(for: .seconds(3))
            if Task.isCancelled == false { withAnimation { message = nil } }
        }
        .sheet(item: $assigning) { location in
            AssignSensorSheet(vehicle: vehicle, location: location, monitor: monitor, settings: settings)
        }
        .sheet(item: $managing) { location in
            if let sensor = monitor.sensor(vehicle: vehicle, location: location) {
                ManageSensorSheet(
                    vehicle: vehicle,
                    location: location,
                    sensor: sensor,
                    monitor: monitor,
                    move: all.count > 1 ? {
                        managing = nil
                        // Nowhere to pick with two wheels, straight to the confirmation
                        if all.count <= 2, let other = all.first(where: { $0 != location }) {
                            confirming = MoveChain(from: location).plus(other)
                        } else {
                            moving = MoveChain(from: location)
                        }
                    } : nil,
                    calibrate: {
                        managing = nil
                        calibrating = sensor
                    },
                    moveToVehicle: { other in
                        managing = nil
                        let move = VehicleMove(location: location, vehicle: other)
                        if let wheel = other.kind.locations.first, other.kind.locations.count == 1 {
                            if monitor.sensor(vehicle: other, location: wheel) != nil {
                                askingMonowheel = move
                            } else {
                                moved(vehicle, move, to: wheel, swap: false)
                            }
                        } else {
                            movingToVehicle = move
                        }
                    }
                )
            }
        }
        .navigationDestination(item: $calibrating) { sensor in
            SensorCalibrationView(vehicle: vehicle, sensor: sensor, monitor: monitor, settings: settings)
        }
        .navigationDestination(item: $movingToVehicle) { move in
            ManageSensorsView(
                vehicleId: move.vehicle.id,
                monitor: monitor,
                settings: settings,
                incoming: IncomingSensor(location: move.location) { target, swap in
                    moved(vehicle, move, to: target, swap: swap)
                    movingToVehicle = nil
                }
            )
        }
        .confirmationDialog(
            asking.map { tr("\($0.last.name) already has a sensor") } ?? "",
            isPresented: Binding(get: { asking != nil }, set: { if $0 == false { asking = nil } }),
            titleVisibility: .visible,
            presenting: asking
        ) { chain in
            Button("Just swap them") {
                asking = nil
                moving = nil
                confirming = chain
            }
            Button("Multi-wheel change") {
                asking = nil
                apply(chain.continuing(all))
            }
            Button("Cancel", role: .cancel) { asking = nil }
        } message: { chain in
            Text("Swap \(chain.start.name) ⇄ \(chain.last.name), or move more sensors around for a tyre rotation.")
        }
        .confirmationDialog(
            askingIncoming.map { tr("\(vehicle.name)'s \($0.phrase) already has a sensor") } ?? "",
            isPresented: Binding(get: { askingIncoming != nil }, set: { if $0 == false { askingIncoming = nil } }),
            titleVisibility: .visible,
            presenting: askingIncoming
        ) { target in
            Button("Swap them") { incoming?.move(target, true) }
            Button("Replace it", role: .destructive) { incoming?.move(target, false) }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("Swapping sends its sensor to the other vehicle's \(incoming?.location.phrase ?? "wheel"). Replacing removes it from \(vehicle.name).")
        }
        .confirmationDialog(
            askingMonowheel.map { tr("\($0.vehicle.name)'s wheel already has a sensor") } ?? "",
            isPresented: Binding(get: { askingMonowheel != nil }, set: { if $0 == false { askingMonowheel = nil } }),
            titleVisibility: .visible,
            presenting: askingMonowheel
        ) { move in
            Button("Swap them") { moved(vehicle, move, to: .single, swap: true) }
            Button("Replace it", role: .destructive) { moved(vehicle, move, to: .single, swap: false) }
            Button("Cancel", role: .cancel) {}
        } message: { move in
            Text("Swapping brings its sensor to the \(move.location.phrase). Replacing removes it from \(move.vehicle.name).")
        }
    }

    private func moved(_ vehicle: Vehicle, _ move: VehicleMove, to target: TyreLocation, swap: Bool) {
        monitor.move(from: vehicle, move.location, to: move.vehicle, target, swap: swap)
        withAnimation { message = swap ? tr("Swapped with \(move.vehicle.name)'s \(target.phrase)") : tr("Moved to \(move.vehicle.name)'s \(target.phrase)") }
    }

    private func apply(_ step: MoveChain.Step) {
        switch step {
        case let .done(chain):
            moving = nil
            confirming = chain
        case let .askSwapOrChain(chain): asking = chain
        case let .proceed(chain): moving = chain
        }
    }

    /// What tapping a wheel does while a sensor is on its way. Once complete, the tyres show as
    /// usual: the arrows tell what's going to happen.
    private func move(all: [TyreLocation], occupied: Set<TyreLocation>) -> TyreMove? {
        if let incoming {
            // Any wheel, its sensor is swapped or replaced
            return TyreMove(canTap: { _ in true }) { target in
                if occupied.contains(target) { askingIncoming = target } else { incoming.move(target, false) }
            }
        }
        guard confirming == nil, let chain = moving else { return nil }
        // Gone through by the chain, unless it can close on it
        let passed = Set(chain.locations.filter { chain.canTap($0, all) == false && $0 != chain.last })
        return TyreMove(waiting: chain.last, passed: passed, canTap: { chain.canTap($0, all) }) { target in
            apply(chain.tap(target, occupied: occupied, all: all))
        }
    }

    /// What to tap, then what the move needs
    @ViewBuilder private func actions(_ vehicle: Vehicle) -> some View {
        VStack(spacing: 8) {
            if incoming != nil {
                prompt(Self.movePrompt)
                cancel { dismiss() }
            } else if let chain = confirming {
                cancel(stop)
                Button {
                    monitor.move(vehicle, chain.moves(occupied: Set(monitor.sensors.filter { $0.vehicleId == vehicle.id }.map(\.location))))
                    stop()
                } label: {
                    Text("Move as shown").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            } else if moving != nil {
                prompt(Self.movePrompt)
                cancel(stop)
            } else {
                prompt(Self.managePrompt)
            }
        }
        .buttonBorderShape(.capsule)
        .controlSize(.large)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func stop() {
        moving = nil
        asking = nil
        confirming = nil
    }

    private func prompt(_ text: String) -> some View {
        Text(text).font(.headline).multilineTextAlignment(.center)
    }

    private func cancel(_ action: @escaping () -> Void) -> some View {
        Button(action: action) { Text("Cancel").frame(maxWidth: .infinity) }
            .buttonStyle(.bordered)
    }
}

/// What can be done with the [sensor] assigned to [location], under its position, brand and ID
struct ManageSensorSheet: View {
    let vehicle: Vehicle
    let location: TyreLocation
    let sensor: BoundSensor
    @ObservedObject var monitor: Monitor
    /// Nil on a vehicle with a single wheel
    let move: (() -> Void)?
    let calibrate: () -> Void
    let moveToVehicle: (Vehicle) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var confirmDelete = false
    @State private var isPickingVehicle = false

    private var otherVehicles: [Vehicle] { monitor.vehicles.filter { $0.id != vehicle.id } }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    SensorOption(title: tr("Move/swap"), subtitle: tr("Assign this sensor to another wheel")) {
                        Image(systemName: "arrow.left.arrow.right")
                    } action: {
                        move?()
                    }
                    .disabled(move == nil)
                    SensorOption(title: tr("Move to another vehicle"), subtitle: tr("Assign this sensor to a wheel of another vehicle")) {
                        Image(systemName: "car")
                    } action: {
                        isPickingVehicle = true
                    }
                    .disabled(otherVehicles.isEmpty)
                    SensorOption(title: tr("Calibrate"), subtitle: tr("Correct this sensor's pressure readings")) {
                        Image(systemName: "slider.horizontal.3")
                    } action: {
                        calibrate()
                    }
                    SensorOption(title: tr("Delete"), subtitle: tr("Remove this sensor from the \(location.phrase)"), color: Theme.error) {
                        Image(systemName: "trash")
                    } action: {
                        confirmDelete = true
                    }
                } header: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Type: \(sensor.brand.name)")
                        // A sensor without an ID has nothing to show, see isIdentifiedByAddress
                        Text(sensor.brand.isIdentifiedByAddress ? tr("No ID, told apart by this iPhone") : "ID: \(sensor.id.asSensorId(sensor.brand))")
                    }
                    .textCase(nil)
                }
            }
            .navigationTitle(location.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
            .navigationDestination(isPresented: $isPickingVehicle) {
                List {
                    Section {
                        ForEach(otherVehicles) { other in
                            SensorOption(title: other.name, subtitle: count(other)) {
                                Image(systemName: "car")
                            } action: {
                                moveToVehicle(other)
                            }
                        }
                    } header: {
                        Text("The \(location.phrase)'s sensor").textCase(nil)
                    }
                }
                .navigationTitle("Move to another vehicle")
                .navigationBarTitleDisplayMode(.inline)
            }
            .alert("Remove the sensor from the \(location.phrase)?", isPresented: $confirmDelete) {
                Button("Remove", role: .destructive) {
                    monitor.unassign(vehicle: vehicle, location: location)
                    dismiss()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This cannot be undone.")
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func count(_ vehicle: Vehicle) -> String {
        switch monitor.sensors.filter({ $0.vehicleId == vehicle.id }).count {
        case 0: tr("No sensors yet")
        case 1: tr("1 sensor")
        case let count: tr("\(count) sensors")
        }
    }
}
