import SwiftUI

/// A settings page's explanation, in body text across the width of its sections, like the Android
/// app's page intros. It's a section's header rather than a clear row: since iOS 26, a row with no
/// insets has its first and last lines cut off by the section's rounded corners.
struct PageIntro<Content: View>: View {
    private let title: LocalizedStringKey?
    private let content: Content

    init(_ title: LocalizedStringKey? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        Section {
        } header: {
            VStack(alignment: .leading, spacing: 8) {
                title.map { Text($0) }
                content
                    .textCase(nil)
                    .font(.body)
                    .foregroundStyle(Color.primary)
            }
            .listRowInsets(EdgeInsets())
        }
    }
}

extension PageIntro where Content == Text {
    init(_ title: LocalizedStringKey? = nil, text: LocalizedStringKey) {
        self.init(title) { Text(text) }
    }
}
