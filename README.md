
# RinLog 2.0.0
[![Build Status](https://travis-ci.org/rinde/RinLog.svg)](https://travis-ci.org/rinde/RinLog) [![DOI](https://zenodo.org/badge/doi/10.5281/zenodo.27361.svg)](http://dx.doi.org/10.5281/zenodo.27361)

Code for experiments in logistics. See [release notes](releasenotes.md). This library uses [semantic versioning](http://semver.org/) and is open source under the [Apache License Version 2.0](LICENSE).
 * Solvers:
     * [CheapestInsertionHeuristic](src/main/java/com/github/rinde/logistics/pdptw/solver/CheapestInsertionHeuristic.java)
     * [Opt2](src/main/java/com/github/rinde/logistics/pdptw/solver/Opt2.java)
 * Agents (can be used via [TruckConfiguration](src/main/java/com/github/rinde/logistics/pdptw/mas/TruckConfiguration.java) or directly via [Truck](src/main/java/com/github/rinde/logistics/pdptw/mas/Truck.java)): 
    * Task allocation:
         * Blackboard ([BlackboardCommModel](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/BlackboardCommModel.java)): 
             * [BlackboardUser](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/BlackboardUser.java)
         * Auctions ([AuctionCommModel](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/AuctionCommModel.java)):
             * [SolverBidder](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/SolverBidder.java)
             * [NegotiatingBidder](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/NegotiatingBidder.java)
             * [RandomBidder](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/RandomBidder.java)
   * Route finding:
         * [SolverRoutePlanner](src/main/java/com/github/rinde/logistics/pdptw/mas/route/SolverRoutePlanner.java)
         * [GotoClosestRoutePlanner](src/main/java/com/github/rinde/logistics/pdptw/mas/route/GotoClosestRoutePlanner.java)
         * [RandomRoutePlanner](src/main/java/com/github/rinde/logistics/pdptw/mas/route/RandomRoutePlanner.java)

