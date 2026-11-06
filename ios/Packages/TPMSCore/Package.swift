// swift-tools-version:5.9
import PackageDescription

// What the iOS app shares with no UI: the decoders, the units, the alerts and the database. Its
// tests run on the Mac (`swift test`), without a phone.
let package = Package(
    name: "TPMSCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "TPMSCore", targets: ["TPMSCore"])],
    targets: [
        .target(name: "TPMSCore"),
        .testTarget(name: "TPMSCoreTests", dependencies: ["TPMSCore"]),
    ],
    swiftLanguageVersions: [.v5]
)
