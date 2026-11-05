// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "OpawKit",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "OpawKit", targets: ["OpawKit"])
    ],
    targets: [
        .target(name: "OpawKit"),
        .testTarget(name: "OpawKitTests", dependencies: ["OpawKit"])
    ]
)
