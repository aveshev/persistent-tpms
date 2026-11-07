import SwiftUI
import TPMSCore

/// A tyre's sensor and its latest readings
struct TyreDetailView: View {
    let vehicle: Vehicle
    let location: TyreLocation
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let sensor = monitor.sensor(vehicle: vehicle, location: location)
        let tyre = monitor.tyres[Monitor.TyreKey(vehicleId: vehicle.id, location: location)]
        NavigationStack {
            List {
                Section("Sensor") {
                    if let sensor {
                        LabeledContent("Brand", value: sensor.brand.name)
                        LabeledContent("ID", value: sensor.brand.isIdentifiedByAddress ? "Told apart by this iPhone" : sensor.id.asSensorId(sensor.brand))
                    } else {
                        Text("No sensor is assigned to this tyre.").foregroundStyle(.secondary)
                    }
                }
                if let tyre, let reading = tyre.latest {
                    Section("Latest reading") {
                        LabeledContent("Pressure", value: reading.pressure.string(settings.pressureUnit) + (tyre.isCalibrated ? "*" : ""))
                        LabeledContent("Temperature", value: reading.temperature.string(settings.temperatureUnit))
                        if let volts = reading.batteryVoltage { LabeledContent("Battery", value: volts.string) }
                        if let percent = reading.batteryPercent { LabeledContent("Battery", value: "\(percent) %") }
                        LabeledContent("Signal", value: "\(reading.rssi) dBm")
                        LabeledContent("Received", value: Date(timeIntervalSince1970: reading.timestamp).formatted(date: .abbreviated, time: .standard))
                        if let status = AdvertisingPacket(bytes: reading.raw).statusBytes {
                            LabeledContent("Status byte", value: status.map { String(format: "%02X", $0) }.joined(separator: " "))
                        }
                    }
                    let thresholds = vehicle.thresholds(location)
                    Section("Alerts") {
                        if tyre.alerts.levels.isEmpty {
                            Text("Everything is within its range.").foregroundStyle(.secondary)
                        }
                        ForEach(AlertClass.allCases, id: \.self) { alertClass in
                            if let level = tyre.alerts.levels[alertClass] {
                                Label(alertText(alertClass, tyre, thresholds), systemImage: "exclamationmark.circle.fill")
                                    .foregroundStyle(level.color)
                            }
                        }
                    }
                    Section("History") {
                        ForEach(monitor.readings(vehicle: vehicle, location: location, limit: 100).reversed(), id: \.timestamp) { reading in
                            HStack {
                                Text(Date(timeIntervalSince1970: reading.timestamp).formatted(date: .numeric, time: .shortened))
                                    .foregroundStyle(.secondary)
                                Spacer()
                                Text(reading.pressure.string(settings.pressureUnit))
                                Text(reading.temperature.string(settings.temperatureUnit)).frame(width: 50, alignment: .trailing)
                            }
                            .font(.callout.monospacedDigit())
                        }
                    }
                }
            }
            .navigationTitle(location.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }

    private func alertText(_ alertClass: AlertClass, _ tyre: Monitor.Tyre, _ thresholds: AlertThresholds) -> String {
        let unit = settings.pressureUnit
        return switch alertClass {
        case .pressure: tyre.alerts.isHighPressure
            ? "Pressure at or near the maximum, \(thresholds.highPressure.string(unit))"
            : "Pressure at or near the minimum, \(thresholds.lowPressure.string(unit))"
        case .temperature: "Temperature at or near the hot one, \(thresholds.highTemp.string(settings.temperatureUnit))"
        case .battery: "Sensor battery low"
        case .pressureLoss: tyre.loss.loss.map { "Lost \($0.drop.string(unit)) since its highest reading of the ride" } ?? "Losing pressure"
        case .sensorAlarm: "The sensor reports a pressure drop. A fall in temperature can raise it too."
        }
    }
}
