import CoreImage.CIFilterBuiltins
import SwiftUI
import TPMSCore

/// A QR code holding the vehicle's sensors, for another phone to assign them all at once by scanning
/// it, like the Android app's ShareSensors. Pecham and Bekubee KY sensors are left out: they carry no
/// ID, and the one this iPhone tells them apart by means nothing to another phone.
struct ShareSensorsView: View {
    let vehicleId: UUID
    @ObservedObject var monitor: Monitor

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let vehicle = monitor.vehicles.first(where: { $0.id == vehicleId }) { content(vehicle) }
            }
            .padding(16)
        }
        .navigationTitle("Share sensors")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder private func content(_ vehicle: Vehicle) -> some View {
        let all = monitor.sensors
            .filter { $0.vehicleId == vehicle.id }
            // In the order of the vehicle's wheels, like the page lists them
            .sorted { vehicle.kind.locations.firstIndex(of: $0.location) ?? 0 < vehicle.kind.locations.firstIndex(of: $1.location) ?? 0 }
        let sensors = all.filter { $0.brand.isIdentifiedByAddress == false }
        if all.isEmpty {
            Text("\(vehicle.name) has no sensor assigned yet, there's nothing to share")
                .multilineTextAlignment(.center)
        } else if sensors.isEmpty {
            Text("\(vehicle.name)'s sensors can't be shared from an iPhone: they carry no ID. On the other phone, assign them via Bluetooth")
                .multilineTextAlignment(.center)
        } else {
            let shared = SharedSensors(kind: vehicle.kind, name: vehicle.name, sensors: sensors.map { sensor in
                SharedSensor(
                    id: sensor.id, location: sensor.location, brand: sensor.brand,
                    isCalibrated: sensor.calibration.isEnabled,
                    offsetKpa: sensor.calibration.calibration.offset.kpa,
                    multiplier: sensor.calibration.calibration.multiplier
                )
            })
            Text("On the other phone, tap a wheel without a sensor, choose “Scan QR code” and point the camera at this code: every sensor is assigned at once")
                .multilineTextAlignment(.center)
            qrCode(shared.uri)
            VStack(alignment: .leading, spacing: 2) {
                Text(shared.name).font(.headline)
                ForEach(shared.sensors, id: \.id) { sensor in
                    Text("\(sensor.location.name): \(sensor.id.asSensorId(sensor.brand))\(sensor.adjusts ? ", calibrated" : "")")
                        .font(.subheadline)
                }
                if sensors.count < all.count {
                    Text("Not shared, they carry no ID: \(all.filter(\.brand.isIdentifiedByAddress).map(\.location.name).joined(separator: ", "))")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding(.top, 8)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// Dark on light whatever the theme, with the quiet zone around it scanners need
    @ViewBuilder private func qrCode(_ code: String) -> some View {
        let filter = CIFilter.qrCodeGenerator()
        let _ = filter.message = Data(code.utf8)
        // Medium, read off a screen it isn't damaged, a smaller code is easier to scan
        let _ = filter.correctionLevel = "M"
        if let output = filter.outputImage, let image = CIContext().createCGImage(output, from: output.extent) {
            Image(decorative: image, scale: 1)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
                .padding(16)
                .background(.white)
                .frame(maxWidth: 320)
        }
    }
}
