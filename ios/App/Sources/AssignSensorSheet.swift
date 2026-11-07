import SwiftUI
import TPMSCore

/// A choice of what to do with a sensor, its icon or badge beside what it does
struct SensorOption<Icon: View>: View {
    let title: String
    let subtitle: String
    var color: Color = Theme.onBackground
    @ViewBuilder let icon: () -> Icon
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                icon()
                    .frame(width: 28)
                    .foregroundStyle(color)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(color)
                    Text(subtitle).font(.footnote).foregroundStyle(Color(.secondaryLabel))
                }
            }
        }
    }
}

/// The ways to assign a sensor to [location], like the Android app's AssignSensorDialog: the sensor
/// detected first when one advertises this location, then by its QR code or by scanning the sensors
/// around
struct AssignSensorSheet: View {
    let vehicle: Vehicle
    let location: TyreLocation
    @ObservedObject var monitor: Monitor
    @ObservedObject var settings: Settings
    @Environment(\.dismiss) private var dismiss
    @State private var path = [Route]()
    @State private var detent = PresentationDetent.medium

    enum Route: Hashable { case qrCode, bluetooth }

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    if let detected = monitor.detected(vehicle: vehicle, location: location) {
                        // The recommended way, under a light tint of the primary color
                        SensorOption(
                            title: "Assign detected sensor",
                            subtitle: "Sysgration, \(location.shortPhrase), ID: \(detected.sensorId.asSensorId(detected.brand))"
                        ) {
                            SysgrationBadge()
                        } action: {
                            monitor.assign(detected, to: vehicle, at: location)
                            dismiss()
                        }
                        .listRowBackground(Theme.primary.opacity(0.12))
                    }
                    SensorOption(title: "Scan QR code", subtitle: "Printed on the sensors' box, card or cap, or shared by another phone") {
                        Image(systemName: "qrcode")
                    } action: {
                        path.append(.qrCode)
                    }
                    SensorOption(title: "Scan via Bluetooth", subtitle: "Use pressure changes to detect the correct sensor") {
                        Image(systemName: "antenna.radiowaves.left.and.right")
                    } action: {
                        path.append(.bluetooth)
                    }
                }
            }
            .navigationTitle("Assign a sensor to the \(location.phrase)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .qrCode:
                    QrCodeScanView(vehicle: vehicle, location: location, monitor: monitor, scanBluetooth: { path = [.bluetooth] }, onDone: { dismiss() })
                case .bluetooth:
                    BluetoothAssignView(vehicle: vehicle, location: location, monitor: monitor, onDone: { dismiss() })
                }
            }
        }
        .presentationDetents([.medium, .large], selection: $detent)
        // Those pages need the whole screen
        .onChange(of: path) { _, path in detent = path.isEmpty ? .medium : .large }
        .onAppear {
            #if DEBUG
            switch Demo.screen {
            case "bluetooth": path = [.bluetooth]
            case "qr": path = [.qrCode]
            default: break
            }
            #endif
        }
    }
}
