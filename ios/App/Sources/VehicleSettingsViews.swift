import SwiftUI
import TPMSCore

/// The vehicle's settings, the alerts being summarised here and edited on their own pages, like the
/// Android app's VehicleSettings
struct VehicleSettingsView: View {
    let vehicleId: UUID
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @Environment(\.dismiss) private var dismiss
    @State private var confirmClear = false
    @State private var confirmDelete = false
    @State private var isRenaming = false
    @State private var name = ""

    var body: some View {
        NavigationStack {
            Group {
                if let vehicle = monitor.vehicles.first(where: { $0.id == vehicleId }) { form(vehicle) }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }

    private func form(_ vehicle: Vehicle) -> some View {
        let isAssigned = monitor.sensors.contains { $0.vehicleId == vehicle.id }
        let unit = settings.pressureUnit
        let front = pressureSummary(vehicle.lowPressure, vehicle.highPressure, unit)
        let rear = vehicle.separateRearPressure ? vehicle.rearLowPressure.flatMap { low in vehicle.rearHighPressure.map { tr("Rear \(pressureSummary(low, $0, unit))") } } : nil
        let spare = vehicle.separateSparePressure ? vehicle.spareLowPressure.flatMap { low in vehicle.spareHighPressure.map { tr("Spare \(pressureSummary(low, $0, unit))") } } : nil
        let others = [rear, spare].compactMap { $0 }
        return Form {
            Section("Alert thresholds") {
                NavigationLink {
                    VehiclePressureSettingsView(vehicleId: vehicle.id, monitor: monitor, settings: settings)
                } label: {
                    SettingsRow(tr("Pressure"), others.isEmpty ? front : ([tr("Front \(front)")] + others).joined(separator: " · "))
                }
                NavigationLink {
                    VehicleTemperatureSettingsView(vehicleId: vehicle.id, monitor: monitor, settings: settings)
                } label: {
                    SettingsRow(tr("Temperature"), tr("Cold \(vehicle.lowTemp.numberString(settings.temperatureUnit)) · Hot \(vehicle.highTemp.numberString(settings.temperatureUnit)) \(settings.temperatureUnit.symbol)"))
                }
                NavigationLink {
                    VehicleBatterySettingsView(vehicleId: vehicle.id, monitor: monitor)
                } label: {
                    SettingsRow(tr("Battery"), monitor.batteryKinds(vehicle).summary(vehicle))
                }
            }
            Section("Sensors") {
                NavigationLink {
                    ManageSensorsView(vehicleId: vehicle.id, monitor: monitor, settings: settings)
                } label: {
                    SettingsRow(tr("Manage sensors"), tr("Move, calibrate, delete"))
                }
                NavigationLink {
                    ShareSensorsView(vehicleId: vehicle.id, monitor: monitor)
                } label: {
                    SettingsRow(tr("Share sensors"), tr("Share with another phone via QR"))
                }
                Button { confirmClear = true } label: {
                    SettingsRow(tr("Clear all sensors"), isAssigned ? tr("Removes every sensor assigned to this vehicle") : tr("No sensor is assigned to this vehicle"), color: Theme.error)
                }
                .disabled(isAssigned == false)
            }
            Section("Vehicle") {
                Button("Edit vehicle name") {
                    name = vehicle.name
                    isRenaming = true
                }
                .foregroundStyle(Theme.onBackground)
                Button { confirmDelete = true } label: {
                    SettingsRow(tr("Delete vehicle"), monitor.vehicles.count > 1 ? nil : tr("The only vehicle cannot be deleted"), color: Theme.error)
                }
                .disabled(monitor.vehicles.count <= 1)
            }
        }
        .navigationTitle(vehicle.name)
        .alert("Vehicle name", isPresented: $isRenaming) {
            TextField("Vehicle name", text: $name)
            Button("Cancel", role: .cancel) {}
            Button("Rename") { monitor.update(vehicle.id) { $0.name = name } }
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .alert("Clear all sensors from \(vehicle.name)?", isPresented: $confirmClear) {
            Button("Clear all", role: .destructive) { monitor.unassignAll(vehicle) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This cannot be undone.")
        }
        .alert("Do you really want to delete \(vehicle.name)?", isPresented: $confirmDelete) {
            Button("Delete \"\(vehicle.name)\"", role: .destructive) {
                monitor.delete(vehicle)
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This action cannot be undone.")
        }
    }

    private func pressureSummary(_ low: Pressure, _ high: Pressure, _ unit: PressureUnit) -> String {
        "\(low.numberString(unit)) – \(high.numberString(unit)) \(unit.symbol)"
    }
}

/// A headline over what it's set to, the Android app's TextSettingsItem
struct SettingsRow: View {
    let headline: String
    let supporting: String?
    var color: Color = Theme.onBackground

    init(_ headline: String, _ supporting: String? = nil, color: Color = Theme.onBackground) {
        self.headline = headline
        self.supporting = supporting
        self.color = color
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(headline).foregroundStyle(color)
            if let supporting {
                Text(supporting).font(.footnote).foregroundStyle(Color(.secondaryLabel))
            }
        }
    }
}

/// What the vehicle's sensors report their battery as. Both while nothing is known, so nothing is
/// hidden from a vehicle being set up.
struct BatteryKinds {
    let hasVoltage: Bool
    let hasPercent: Bool

    var showsVoltage: Bool { hasVoltage || hasPercent == false }
    var showsPercent: Bool { hasPercent || hasVoltage == false }

    func summary(_ vehicle: Vehicle) -> String {
        switch (showsVoltage, showsPercent) {
        case (true, false): tr("Low voltage alarm \(vehicle.lowBatteryVoltage.string)")
        case (false, true): tr("Low battery alarm \(vehicle.lowBatteryPercent)%")
        default: tr("Low alarms \(vehicle.lowBatteryVoltage.string) · \(vehicle.lowBatteryPercent)%")
        }
    }
}

extension Monitor {
    func batteryKinds(_ vehicle: Vehicle) -> BatteryKinds {
        let brands = sensors.filter { $0.vehicleId == vehicle.id }.map(\.brand)
        return BatteryKinds(hasVoltage: brands.contains { $0.reportsPercent == false }, hasPercent: brands.contains(where: \.reportsPercent))
    }
}

/// A number being edited, typed in or nudged by [step] with the −/+ buttons
struct NumberEdit: Identifiable {
    let title: String
    let value: Float
    let range: ClosedRange<Float>
    let step: Float
    let unit: String
    let format: (Float) -> String
    let onConfirm: (Float) -> Void

    var id: String { title }
}

/// Like the Android app's NumberDialog: confirming is only possible once the text is a number within
/// the range, the range being shown below the field instead of silently clamping what was typed
struct NumberDialog: View {
    let edit: NumberEdit
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @FocusState private var isFocused: Bool

    private var tolerance: Float { edit.step * 0.01 }

    /// Both decimal separators are read
    private var parsed: Float? {
        Float(text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: "."))
    }

    /// The bounds are displayed rounded, typing them as displayed must still be accepted
    private var accepted: Float? {
        parsed.flatMap { value in
            value >= edit.range.lowerBound - tolerance && value <= edit.range.upperBound + tolerance
                ? min(max(value, edit.range.lowerBound), edit.range.upperBound)
                : nil
        }
    }

    var body: some View {
        let current = parsed ?? edit.value
        NavigationStack {
            VStack(spacing: 6) {
                HStack(spacing: 8) {
                    Button {
                        // The small offset keeps a value sitting on a step, despite float errors, on it
                        nudge(((current / edit.step - 0.01).rounded(.up) - 1) * edit.step)
                    } label: {
                        Image(systemName: "minus").frame(width: 32, height: 32)
                    }
                    .disabled(current <= edit.range.lowerBound + tolerance)
                    .accessibilityLabel("Decrease")
                    HStack(spacing: 4) {
                        TextField(edit.title, text: $text)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.center)
                            .font(.title2)
                            .focused($isFocused)
                        Text(edit.unit).foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10))
                    Button {
                        nudge(((current / edit.step + 0.01).rounded(.down) + 1) * edit.step)
                    } label: {
                        Image(systemName: "plus").frame(width: 32, height: 32)
                    }
                    .disabled(current >= edit.range.upperBound - tolerance)
                    .accessibilityLabel("Increase")
                }
                .buttonStyle(.bordered)
                Text("From \(edit.format(edit.range.lowerBound)) to \(edit.format(edit.range.upperBound)) \(edit.unit)")
                    .font(.footnote)
                    .foregroundStyle(accepted == nil ? Theme.error : Color(.secondaryLabel))
                Spacer(minLength: 0)
            }
            .padding(16)
            .navigationTitle(edit.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("OK") {
                        accepted.map(edit.onConfirm)
                        dismiss()
                    }
                    .disabled(accepted == nil)
                }
            }
        }
        .presentationDetents([.height(190)])
        .onAppear {
            text = edit.format(edit.value)
            isFocused = true
        }
        // Starts fully selected so typing replaces the value
        .onReceive(NotificationCenter.default.publisher(for: UITextField.textDidBeginEditingNotification)) { notification in
            DispatchQueue.main.async { (notification.object as? UITextField)?.selectAll(nil) }
        }
    }

    private func nudge(_ next: Float) {
        text = edit.format(min(max(next, edit.range.lowerBound), edit.range.upperBound))
    }
}

/// Demo tyres drawn like the real ones, each captioned with what its colour means, above an
/// explanation, like the Android app's TyreLegend
struct TyreLegend: View {
    enum Sample {
        case fill(Color), alerting, critical
    }

    let text: String
    let entries: [(sample: Sample, caption: String)]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            TimelineView(.periodic(from: .now, by: 0.1)) { context in
                HStack(alignment: .top) {
                    ForEach(Array(entries.enumerated()), id: \.offset) { _, entry in
                        VStack(spacing: 8) {
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(color(entry.sample))
                                .opacity(isHidden(entry.sample, context.date) ? 0 : 1)
                                .frame(width: 36, height: 88)
                            Text(entry.caption)
                                .font(.footnote)
                                .multilineTextAlignment(.center)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
            Text(text)
        }
    }

    private func color(_ sample: Sample) -> Color {
        if case let .fill(color) = sample { color } else { Theme.error }
    }

    private func isHidden(_ sample: Sample, _ date: Date) -> Bool {
        switch sample {
        case .fill: false
        case .alerting: Blink.red.isFirstPhase(date) == false
        case .critical: Blink.critical.isFirstPhase(date) == false
        }
    }
}

/// The page editing the pressure range the vehicle's tyres are expected to stay within
struct VehiclePressureSettingsView: View {
    let vehicleId: UUID
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @State private var editing: NumberEdit?

    private enum Axle { case front, rear, spare }

    private static let limits = Pressure.psi(0)...Pressure.psi(150)

    var body: some View {
        Group {
            if let vehicle = monitor.vehicles.first(where: { $0.id == vehicleId }) { form(vehicle) }
        }
        .navigationTitle("Pressure")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $editing) { NumberDialog(edit: $0) }
    }

    private func form(_ vehicle: Vehicle) -> some View {
        let canSeparateRear = vehicle.kind.hasFrontRearAxles
        let canSeparateSpare = vehicle.kind.locations.contains(.spare)
        let rear = vehicle.separateRearPressure ? vehicle.rearLowPressure.flatMap { low in vehicle.rearHighPressure.map { low...max(low, $0) } } : nil
        let spare = vehicle.separateSparePressure ? vehicle.spareLowPressure.flatMap { low in vehicle.spareHighPressure.map { low...max(low, $0) } } : nil
        return Form {
            Section {
                TyreLegend(
                    text: tr("When a sensor reports pressure outside of the range set here, it will raise a warning alert and blink the affected tyre indicator in red. If it's more than 20% above the maximum, or more than 25% below the minimum, it will be a critical alert. Getting within 3% of the minimum or maximum will raise a caution alert."),
                    entries: [(.fill(Theme.primary), tr("In range")), (.alerting, tr("Out of range")), (.critical, tr("Critical"))]
                )
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }
            if canSeparateRear || canSeparateSpare {
                Section {
                    if canSeparateRear {
                        Toggle(isOn: Binding(get: { rear != nil }, set: { enabled in
                            monitor.update(vehicleId) { vehicle in
                                // The front range is only copied the first time, see VehicleRangesUseCase
                                if enabled, vehicle.rearLowPressure == nil || vehicle.rearHighPressure == nil {
                                    vehicle.rearLowPressure = vehicle.lowPressure
                                    vehicle.rearHighPressure = vehicle.highPressure
                                }
                                vehicle.separateRearPressure = enabled
                            }
                        })) {
                            SettingsRow(tr("Set rear pressure separately"), tr("Turn on if the rear tyres need a different pressure than the front ones"))
                        }
                    }
                    if canSeparateSpare {
                        Toggle(isOn: Binding(get: { spare != nil }, set: { enabled in
                            monitor.update(vehicleId) { vehicle in
                                if enabled, vehicle.spareLowPressure == nil || vehicle.spareHighPressure == nil {
                                    vehicle.spareLowPressure = vehicle.lowPressure
                                    vehicle.spareHighPressure = vehicle.highPressure
                                }
                                vehicle.separateSparePressure = enabled
                            }
                        })) {
                            SettingsRow(tr("Set spare pressure separately"), tr("Turn on if the spare needs a different pressure than the front tyres"))
                        }
                    }
                }
            }
            range(frontHeader(canSeparateRear && rear == nil, canSeparateSpare && spare == nil, separable: canSeparateRear || canSeparateSpare), vehicle.lowPressure...max(vehicle.lowPressure, vehicle.highPressure), .front)
            if let rear { range(tr("Rear"), rear, .rear) }
            if let spare { range(tr("Spare"), spare, .spare) }
        }
    }

    /// Every location the front range applies to: "Front, rear & spare", "Front & spare", "Front"…
    private func frontHeader(_ withRear: Bool, _ withSpare: Bool, separable: Bool) -> String {
        guard separable else { return tr("Expected range") }
        let names = [tr("Front"), withRear ? tr("rear") : nil, withSpare ? tr("spare") : nil].compactMap { $0 }
        return names.count == 1 ? names[0] : names.dropLast().joined(separator: ", ") + " & " + names[names.count - 1]
    }

    private func range(_ header: String, _ range: ClosedRange<Pressure>, _ axle: Axle) -> some View {
        let unit = settings.pressureUnit
        return Section(header) {
            Button {
                edit(tr("Minimum pressure"), range.lowerBound, Self.limits.lowerBound...range.upperBound) { pressure in
                    monitor.update(vehicleId) { vehicle in
                        switch axle {
                        case .front: vehicle.lowPressure = pressure
                        case .rear: vehicle.rearLowPressure = pressure
                        case .spare: vehicle.spareLowPressure = pressure
                        }
                    }
                }
            } label: {
                SettingsRow(tr("Minimum"), range.lowerBound.string(unit))
            }
            Button {
                edit(tr("Maximum pressure"), range.upperBound, range.lowerBound...Self.limits.upperBound) { pressure in
                    monitor.update(vehicleId) { vehicle in
                        switch axle {
                        case .front: vehicle.highPressure = pressure
                        case .rear: vehicle.rearHighPressure = pressure
                        case .spare: vehicle.spareHighPressure = pressure
                        }
                    }
                }
            } label: {
                SettingsRow(tr("Maximum"), range.upperBound.string(unit))
            }
        }
    }

    private func edit(_ title: String, _ value: Pressure, _ range: ClosedRange<Pressure>, _ set: @escaping (Pressure) -> Void) {
        let unit = settings.pressureUnit
        editing = NumberEdit(
            title: title,
            value: value.convert(unit),
            range: range.lowerBound.convert(unit)...range.upperBound.convert(unit),
            step: unit.step,
            unit: unit.symbol,
            format: { Pressure($0, unit).numberString(unit) },
            onConfirm: { set(Pressure($0, unit)) }
        )
    }
}

extension PressureUnit {
    /// How much the −/+ buttons move a pressure, a common gauge resolution in each unit
    var step: Float {
        switch self {
        case .kiloPascal: 10
        case .bar: 0.1
        case .psi: 1
        }
    }

    /// Finer than the alert range's steps, a calibration corrects a few units at most
    var offsetStep: Float { step / 2 }
}

/// The page editing the temperatures giving the vehicle's tyres their colour
struct VehicleTemperatureSettingsView: View {
    let vehicleId: UUID
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @State private var editing: NumberEdit?

    /// Winter tyres stay supple far below summer ones
    private static let limits = Temperature(celsius: -40)...Temperature(celsius: 150)
    private static let warmUp: Float = 10

    var body: some View {
        Group {
            if let vehicle = monitor.vehicles.first(where: { $0.id == vehicleId }) { form(vehicle) }
        }
        .navigationTitle("Temperature")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $editing) { NumberDialog(edit: $0) }
    }

    private func form(_ vehicle: Vehicle) -> some View {
        let unit = settings.temperatureUnit
        let low = vehicle.lowTemp, high = vehicle.highTemp
        // The colours' and the alert levels' margins, in degrees of the displayed unit
        let degrees = { (celsius: Float) in localizedNumber("%.0f %@", unit == .celsius ? celsius : celsius * 1.8, unit.symbol) }
        let half = max(0, (high.celsius - low.celsius) / 2)
        let fineStart = Temperature(celsius: low.celsius + min(Self.warmUp, half))
        let fineEnd = Temperature(celsius: high.celsius - min(AlertThresholds.amberTemperatureBelow, half))
        let critical = Temperature(celsius: high.celsius + AlertThresholds.crimsonTemperatureAbove)
        return Form {
            Section {
                TyreLegend(
                    text: tr("A tyre indicator is blue up to the cold temperature, where the rubber gets too stiff, and turns green over the next \(degrees(Self.warmUp)). It stays green until \(degrees(AlertThresholds.amberTemperatureBelow)) below hot, then turns towards red. Going above hot will raise a warning alert and blink the affected tyre indicator in red. Going \(degrees(AlertThresholds.crimsonTemperatureAbove)) above hot will raise a critical alert."),
                    entries: [
                        (.fill(Theme.tertiary), tr("Cold\n\(low.numberString(unit)) \(unit.symbol)")),
                        (.fill(Theme.primary), tr("OK\n\(fineStart.numberString(unit)) – \(fineEnd.numberString(unit)) \(unit.symbol)")),
                        (.alerting, tr("Hot\n\(high.numberString(unit)) \(unit.symbol)")),
                        (.critical, tr("Critical\n\(critical.numberString(unit)) \(unit.symbol)")),
                    ]
                )
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }
            Section("Temperatures") {
                // Cold stays below hot so the colours keep their order
                Button {
                    edit(tr("Cold temperature"), low, Self.limits.lowerBound...high) { value in monitor.update(vehicleId) { $0.lowTemp = value } }
                } label: {
                    SettingsRow(tr("Cold"), "\(low.numberString(unit)) \(unit.symbol)")
                }
                Button {
                    edit(tr("Hot temperature"), high, low...Self.limits.upperBound) { value in monitor.update(vehicleId) { $0.highTemp = value } }
                } label: {
                    SettingsRow(tr("Hot"), "\(high.numberString(unit)) \(unit.symbol)")
                }
            }
        }
    }

    private func edit(_ title: String, _ value: Temperature, _ range: ClosedRange<Temperature>, _ set: @escaping (Temperature) -> Void) {
        let unit = settings.temperatureUnit
        editing = NumberEdit(
            title: title,
            value: value.convert(unit),
            range: range.lowerBound.convert(unit)...range.upperBound.convert(unit),
            step: 1,
            unit: unit.symbol,
            format: { Temperature($0, unit).numberString(unit) },
            onConfirm: { set(Temperature($0, unit)) }
        )
    }
}

/// The page editing the levels at which a sensor's battery alarms: its voltage for most sensors, a
/// percentage for Sysgration's. Only the ones the vehicle's sensors report are shown.
struct VehicleBatterySettingsView: View {
    let vehicleId: UUID
    @ObservedObject var monitor: Monitor
    @State private var editing: NumberEdit?

    var body: some View {
        Group {
            if let vehicle = monitor.vehicles.first(where: { $0.id == vehicleId }) { form(vehicle) }
        }
        .navigationTitle("Battery")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $editing) { NumberDialog(edit: $0) }
    }

    private func form(_ vehicle: Vehicle) -> some View {
        let kinds = monitor.batteryKinds(vehicle)
        let voltage = vehicle.lowBatteryVoltage
        let percent = vehicle.lowBatteryPercent
        let margin = AlertThresholds.batteryAmberMargin
        let marginPercent = AlertThresholds.batteryAmberMarginPercent
        return Form {
            if kinds.showsVoltage {
                Section {
                    samples(
                        normal: Voltage(volts: voltage.volts + margin * 2).string,
                        gettingLow: Voltage(volts: voltage.volts + margin).string,
                        low: voltage.string
                    )
                }
                Section {
                    Button {
                        editing = NumberEdit(
                            title: tr("Low voltage alarm"), value: voltage.volts,
                            // Below 2 V the sensors stop sending before reporting it, above 3 V a fresh battery would alarm
                            range: 2...3,
                            // The sensors report their voltage in steps of 0.1 V
                            step: 0.1, unit: "V",
                            format: { localizedNumber("%.1f", $0) },
                            onConfirm: { value in monitor.update(vehicleId) { $0.lowBatteryVoltage = Voltage(volts: value) } }
                        )
                    } label: {
                        SettingsRow(tr("Low voltage alarm"), voltage.string)
                    }
                } header: {
                    Text("Voltage alarm")
                } footer: {
                    Text("If the sensor reports battery voltage, you can set when to report it as low. Within 0.1 V of the setting, it's shown in orange. After 10 minutes of consistently low readings it will raise a warning alert. The app will use a slightly reduced value when temperatures are low, to account for the voltage drop.")
                }
            }
            if kinds.showsPercent {
                Section {
                    samples(normal: "\(percent + marginPercent * 2)%", gettingLow: "\(percent + marginPercent)%", low: "\(percent)%")
                }
                Section {
                    Button {
                        editing = NumberEdit(
                            title: tr("Low battery alarm"), value: Float(percent),
                            // Up to half the battery, past which a fresh battery is never far from alarming
                            range: 5...50,
                            step: 5, unit: "%",
                            format: { String(Int($0.rounded())) },
                            onConfirm: { value in monitor.update(vehicleId) { $0.lowBatteryPercent = Int(value.rounded()) } }
                        )
                    } label: {
                        SettingsRow(tr("Low battery alarm"), "\(percent)%")
                    }
                } header: {
                    Text("Percentage alarm")
                } footer: {
                    Text("Sysgration sensors report their battery state as a percentage rather than a voltage.\n\nAt or below the setting, it will blink red. After 10 minutes of consistently low readings it will raise a warning alert.")
                }
            }
        }
    }

    /// A normal, a getting low and a low battery, as the main screen shows them
    private func samples(normal: String, gettingLow: String, low: String) -> some View {
        TimelineView(.periodic(from: .now, by: 0.1)) { context in
            HStack(alignment: .top) {
                sample(normal, Theme.onBackground, tr("Normal"), isHidden: false)
                sample(gettingLow, Theme.orange, tr("Getting low"), isHidden: false)
                sample(low, Theme.error, tr("Low alarm"), isHidden: Blink.red.isFirstPhase(context.date) == false)
            }
        }
        .listRowBackground(Color.clear)
    }

    private func sample(_ value: String, _ color: Color, _ caption: String, isHidden: Bool) -> some View {
        VStack(spacing: 8) {
            Text(value).fontWeight(.semibold).foregroundStyle(color).opacity(isHidden ? 0 : 1)
            Text(caption).font(.footnote)
        }
        .frame(maxWidth: .infinity)
    }
}

/// The page correcting the pressure read by a sensor. Its edits are only a draft until applied to
/// this sensor or to every sensor of the vehicle, back discards them, like the Android app's
/// SensorCalibrationSettings.
struct SensorCalibrationView: View {
    let vehicle: Vehicle
    let sensor: BoundSensor
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @Environment(\.dismiss) private var dismiss
    @State private var draft: CalibrationDraft
    @State private var editing: NumberEdit?
    @State private var confirmErase = false
    @State private var confirmDiscard = false

    /// Far more than "a few units" off, a sensor that far off is broken rather than miscalibrated
    private static let offsetLimit = Pressure.psi(20)
    private static let minExampleKpa: Float = 10

    init(vehicle: Vehicle, sensor: BoundSensor, monitor: Monitor, settings: Settings) {
        self.vehicle = vehicle
        self.sensor = sensor
        self.monitor = monitor
        self.settings = settings
        _draft = State(initialValue: CalibrationDraft(
            saved: sensor.calibration,
            others: monitor.sensors.filter { $0.vehicleId == vehicle.id && $0.id != sensor.id }.map(\.calibration)
        ))
    }

    var body: some View {
        let unit = settings.pressureUnit
        let calibration = draft.draft.calibration
        let isEnabled = draft.draft.isEnabled
        // What the sensor reads, or somewhere the user expects their tyres to be: the example speaks
        // to them. A flat or removed tyre's reading wouldn't show much.
        let read = monitor.tyres[Monitor.TyreKey(vehicleId: vehicle.id, location: sensor.location)]?.latestRead.flatMap { $0.kpa > Self.minExampleKpa ? $0 : nil }
        let example = read ?? Pressure(kpa: (vehicle.lowPressure.kpa + vehicle.highPressure.kpa) / 2)
        Form {
            Section {
                Text("If a sensor is reporting incorrect pressure values, you can adjust it here.\nKeep the multiplier at default (×1.00), unless you're really sure you need to change it.")
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }
            Section {
                Toggle("Pressure calibration", isOn: $draft.draft.isEnabled)
            }
            Section {
                // Greyed out rather than hidden while the calibration is off
                Button {
                    editing = NumberEdit(
                        title: tr("Offset"), value: calibration.offset.convert(unit),
                        range: -Self.offsetLimit.convert(unit)...Self.offsetLimit.convert(unit),
                        step: unit.offsetStep, unit: unit.symbol,
                        format: { Pressure($0, unit).numberString(unit) },
                        onConfirm: { draft.draft.calibration.offset = Pressure($0, unit) }
                    )
                } label: {
                    SettingsRow(tr("Offset"), (calibration.offset.kpa > 0 ? "+" : "") + calibration.offset.string(unit))
                }
                Button {
                    editing = NumberEdit(
                        title: tr("Multiplier"), value: calibration.multiplier,
                        range: 0.5...1.5,
                        step: 0.01, unit: "×",
                        format: { localizedNumber("%.2f", $0) },
                        onConfirm: { draft.draft.calibration.multiplier = $0 }
                    )
                } label: {
                    SettingsRow(tr("Multiplier"), localizedNumber("×%.2f", calibration.multiplier))
                }
            } header: {
                Text("Correction")
            } footer: {
                // "read as": the corrected pressure isn't only shown, the alerts check it too
                read != nil
                    ? Text("Example: \(example.string(unit)) reported by this sensor is read as \(calibration.applyTo(example).string(unit))* (asterisk added when displaying corrected values)")
                    : Text("Example: \(example.string(unit)) reported by a sensor is read as \(calibration.applyTo(example).string(unit))* (asterisk added when displaying corrected values)")
            }
            .disabled(isEnabled == false)
            .opacity(isEnabled ? 1 : 0.38)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) { actions }
        .navigationTitle("Calibrate sensor")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(draft.isChanged)
        .toolbar {
            if draft.isChanged {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Back", systemImage: "chevron.backward") { confirmDiscard = true }
                }
            }
        }
        .sheet(item: $editing) { NumberDialog(edit: $0) }
        .alert("Applying to all will erase other sensors' calibration, are you sure?", isPresented: $confirmErase) {
            Button("Yes, erase them", role: .destructive, action: applyToAll)
            Button("Cancel", role: .cancel) {}
        }
        .alert("Discard your changes?", isPresented: $confirmDiscard) {
            Button("Discard", role: .destructive) { dismiss() }
            Button("Cancel", role: .cancel) {}
        }
    }

    /// Applying to all is the recommended one: the sensors of a vehicle usually read alike. A vehicle
    /// with this sensor only has nothing to apply it to, applying to it is all there is.
    private var actions: some View {
        VStack(spacing: 8) {
            let toSensor = sensor.location == .single ? tr("Apply to this sensor") : tr("Apply to \(sensor.location.shortPhrase) sensor")
            if draft.others.isEmpty {
                Button(action: applyToSensor) { Text(toSensor).frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent)
                    .disabled(draft.isChanged == false)
            } else {
                Button(action: applyToSensor) { Text(toSensor).frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
                    .disabled(draft.isChanged == false)
                Button {
                    if draft.applyingErases { confirmErase = true } else { applyToAll() }
                } label: {
                    Text("Apply to all \(vehicle.name) sensors").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(draft.canApplyToAll == false)
            }
        }
        .buttonBorderShape(.capsule)
        .controlSize(.large)
        .padding(16)
        .background(.bar)
    }

    private func applyToSensor() {
        monitor.setCalibration(draft.toApply, sensorIds: [sensor.id])
        dismiss()
    }

    private func applyToAll() {
        monitor.setCalibration(draft.toApply, sensorIds: monitor.sensors.filter { $0.vehicleId == vehicle.id }.map(\.id))
        dismiss()
    }
}
