/// Sensors moving along [locations]: each one's sensor goes to the next location, like the Android
/// app's MoveChain. The first one's sensor started the move, the others are where the user sent
/// each sensor in turn.
public struct MoveChain: Equatable, Sendable {
    public let locations: [TyreLocation]

    /// The chain's length from which its last sensor can go back to the start
    private static let closingSize = 3

    public init(from start: TyreLocation) { locations = [start] }

    private init(locations: [TyreLocation]) { self.locations = locations }

    public var start: TyreLocation { locations[0] }

    /// Where the sensor that moved last was sent to, the one now asking for a location
    public var last: TyreLocation { locations[locations.count - 1] }

    /// The locations the last sensor can be sent to, among the vehicle's [all]: those not in the
    /// chain yet, and the start once the chain goes through three locations or more, closing it.
    /// Sent back to the start from the second location would be a swap.
    public func options(_ all: [TyreLocation]) -> [TyreLocation] {
        all.filter { locations.contains($0) == false || ($0 == start && locations.count >= Self.closingSize) }
    }

    public func canTap(_ location: TyreLocation, _ all: [TyreLocation]) -> Bool {
        options(all).contains(location)
    }

    public func plus(_ location: TyreLocation) -> MoveChain {
        MoveChain(locations: locations + [location])
    }

    /// The moves picked so far, each location's sensor to the next one
    public var picked: [Move] {
        zip(locations, locations.dropFirst()).map { Move(from: $0, to: $1) }
    }

    /// The moves, each location's sensor to the next one. When the last location has a sensor too
    /// ([occupied]), it goes to the start, which the first move left empty: a rotation.
    public func moves(occupied: Set<TyreLocation>) -> [Move] {
        picked + (occupied.contains(last) && locations.count > 1 ? [Move(from: last, to: start)] : [])
    }

    /// What tapping [target] does, [occupied] being the vehicle's locations with a sensor among
    /// [all] of them: the start or an empty location ends the move there. An occupied one asks, for
    /// the first one, whether to just swap the sensors on a vehicle with more than two locations,
    /// and continues the chain otherwise, see continuing.
    public func tap(_ target: TyreLocation, occupied: Set<TyreLocation>, all: [TyreLocation]) -> Step {
        precondition(canTap(target, all), "\(target) can't be tapped in the chain \(locations)")
        // Closed, the last sensor goes to the start, see moves
        if target == start { return .done(self) }
        let chain = plus(target)
        if occupied.contains(target) == false || all.count <= 2 { return .done(chain) }
        return locations.count == 1 ? .askSwapOrChain(chain) : chain.continuing(all)
    }

    /// Asks for the next location, or ends the move once there's a single one left to pick: the
    /// start closes the chain, another location gets the last sensor, and its own goes to the start
    /// if it has one
    public func continuing(_ all: [TyreLocation]) -> Step {
        let options = options(all)
        if options.count > 1 { return .proceed(self) }
        if let single = options.first, single != start { return .done(plus(single)) }
        return .done(self)
    }

    public struct Move: Hashable, Sendable {
        public let from: TyreLocation
        public let to: TyreLocation

        public init(from: TyreLocation, to: TyreLocation) {
            self.from = from
            self.to = to
        }
    }

    public enum Step: Equatable, Sendable {
        /// Ready to be applied, see moves
        case done(MoveChain)
        /// The chain's last location has a sensor: swap them, see done, or continue, see continuing
        case askSwapOrChain(MoveChain)
        /// Waiting for the location of the chain's last sensor
        case proceed(MoveChain)
    }
}
