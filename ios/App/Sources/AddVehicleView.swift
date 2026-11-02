import SwiftUI
import TPMSCore

/// A new vehicle, which becomes the current one, like the Android app's "Add a vehicle". Its kind
/// can't change afterwards: its wheels are what sensors and readings are kept by.
struct AddVehicleView: View {
    @ObservedObject var monitor: Monitor
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    /// None at first, like on Android: it can't be changed afterwards
    @State private var kind: VehicleKind?
    @FocusState private var isFocused: Bool

    var body: some View {
        NavigationStack {
            Form {
                TextField("Vehicle name", text: $name)
                    .textInputAutocapitalization(.sentences)
                    .focused($isFocused)
                Picker("Kind", selection: $kind) {
                    ForEach(VehicleKind.addingOrder) { Text($0.name).tag(VehicleKind?.some($0)) }
                }
                .pickerStyle(.inline)
            }
            .navigationTitle("Add a new vehicle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        kind.map { monitor.add(name: name.trimmingCharacters(in: .whitespaces), kind: $0) }
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || kind == nil)
                }
            }
            .onAppear { isFocused = true }
        }
    }
}
