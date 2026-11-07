#if DEBUG
import SwiftUI
import TPMSCore

/// Sends a reading for a tyre of the current vehicle, like the Android app's "Simulate a reading":
/// as its sensor's, shown and alerted on like a real one. Not stored, it has no bytes to store.
struct SimulateReadingView: View {
    let vehicle: Vehicle
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @Environment(\.dismiss) private var dismiss
    @State private var location: TyreLocation
    @State private var pressure = ""
    @State private var temperature: String
    @State private var battery = ""
    @State private var isAlarm = false

    init(vehicle: Vehicle, monitor: Monitor, settings: Settings) {
        self.vehicle = vehicle
        self.monitor = monitor
        self.settings = settings
        _location = State(initialValue: vehicle.kind.locations.first ?? .single)
        _temperature = State(initialValue: Temperature(celsius: 20).numberString(settings.temperatureUnit))
    }

    private func number(_ text: String) -> Float? {
        Float(text.replacingOccurrences(of: ",", with: "."))
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker("Tyre", selection: $location) {
                    ForEach(vehicle.kind.locations) { Text($0.name).tag($0) }
                }
                LabeledContent("Pressure (\(settings.pressureUnit.symbol))") {
                    TextField("Pressure", text: $pressure).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                }
                LabeledContent("Temperature (\(settings.temperatureUnit.symbol))") {
                    TextField("Temperature", text: $temperature).keyboardType(.numbersAndPunctuation).multilineTextAlignment(.trailing)
                }
                LabeledContent("Battery (optional): V, or % for Sysgration") {
                    TextField("Battery", text: $battery).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                }
                Toggle("Sensor's own alarm", isOn: $isAlarm)
            }
            .navigationTitle("Simulate a reading")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Send") {
                        monitor.simulate(
                            vehicle, location,
                            pressure: Pressure(number(pressure) ?? 0, settings.pressureUnit),
                            temperature: Temperature(number(temperature) ?? 0, settings.temperatureUnit),
                            battery: number(battery),
                            isAlarm: isAlarm
                        )
                    }
                    .disabled(number(pressure) == nil || number(temperature) == nil)
                }
            }
        }
    }
}
#endif
