// Draws the app icon from the Android launcher icon's shapes (app/phone/src/main/res/drawable/
// ic_launcher_foreground.xml, Persistent TPMS Lite's colours): `swift make-icon.swift <out.png>`
import AppKit

let size = 1024
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size, bitsPerSample: 8, samplesPerPixel: 4,
                           hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
let context = NSGraphicsContext.current!.cgContext

func color(_ hex: UInt32) -> CGColor {
    CGColor(red: CGFloat(hex >> 16 & 0xFF) / 255, green: CGFloat(hex >> 8 & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
}

context.setFillColor(color(0x101410))
context.fill(CGRect(x: 0, y: 0, width: size, height: size))

// The Android icon's 432 grid, y downwards, whose middle 288 an iOS icon shows in full
let scale = CGFloat(size) / 288
context.translateBy(x: 0, y: CGFloat(size))
context.scaleBy(x: scale, y: -scale)
context.translateBy(x: -72, y: -72)

let glyph = color(0x4CAF50)
// The tyre's outline, open at the top between the stems and cut flat by the tread
context.saveGState()
let clip = CGMutablePath()
clip.addLines(between: [CGPoint(x: 0, y: 0), CGPoint(x: 169.5, y: 0), CGPoint(x: 169.5, y: 150), CGPoint(x: 262.5, y: 150),
                        CGPoint(x: 262.5, y: 0), CGPoint(x: 432, y: 0), CGPoint(x: 432, y: 290), CGPoint(x: 0, y: 290)])
clip.closeSubpath()
context.addPath(clip)
context.clip()
let ring = CGMutablePath()
ring.addEllipse(in: CGRect(x: 216 - 93, y: 207 - 93, width: 186, height: 186))
ring.addEllipse(in: CGRect(x: 216 - 75, y: 207 - 75, width: 150, height: 150))
context.addPath(ring)
context.setFillColor(glyph)
context.fillPath(using: .evenOdd)
context.restoreGState()

// Stems, exclamation mark, tread and its four blocks
context.setFillColor(glyph)
for (x1, y1, x2, y2) in [(151, 123, 169.5, 150), (262.5, 123, 281, 150), (206.75, 151, 225.25, 207), (206.75, 226, 225.25, 244),
                         (151, 272, 281, 290), (151, 290, 169.5, 309.5), (188, 290, 206.5, 309.5), (225.5, 290, 244, 309.5),
                         (262.5, 290, 281, 309.5)] as [(CGFloat, CGFloat, CGFloat, CGFloat)] {
    context.fill(CGRect(x: x1, y: y1, width: x2 - x1, height: y2 - y1))
}

// The "always on" dot
context.setFillColor(color(0x8BE08F))
context.fillEllipse(in: CGRect(x: 291, y: 277, width: 30, height: 30))

NSGraphicsContext.current = nil
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
