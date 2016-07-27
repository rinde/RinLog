# RinLog 3.0.0
[![Javadocs](https://javadoc.io/badge/com.github.rinde/rinlog.svg?color=red)](https://javadoc.io/doc/com.github.rinde/rinlog)
[![Build Status](https://travis-ci.org/rinde/RinLog.svg)](https://travis-ci.org/rinde/RinLog) 
[![DOI](https://zenodo.org/badge/doi/10.5281/zenodo.58848.svg)](http://dx.doi.org/10.5281/zenodo.58848)

Code for experiments in logistics. See [release notes](releasenotes.md). This library uses [semantic versioning](http://semver.org/) and is open source under the [Apache License Version 2.0](LICENSE).
 * Solvers:
     * [CheapestInsertionHeuristic](src/main/java/com/github/rinde/logistics/pdptw/solver/CheapestInsertionHeuristic.java)
     * [Opt2](src/main/java/com/github/rinde/logistics/pdptw/solver/Opt2.java) real-time and simulated time.
     * [OptaPlannerSolvers](src/main/java/com/github/rinde/logistics/pdptw/solver/optaplanner/OptaplannerSolvers.java) real-time and simulated time.
 * Agents (can be used via [TruckFactory](src/main/java/com/github/rinde/logistics/pdptw/mas/TruckFactory.java) or directly via [Truck](src/main/java/com/github/rinde/logistics/pdptw/mas/Truck.java)): 
    * Task allocation:
         * Blackboard ([BlackboardCommModel](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/BlackboardCommModel.java)): 
             * [BlackboardUser](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/BlackboardUser.java)
         * Auctions ([AuctionCommModel](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/AuctionCommModel.java)):
             * [SolverBidder](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/SolverBidder.java)
             * [RtSolverBidder](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/RtSolverBidder.java) for real-time simulation.
             * [NegotiatingBidder](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/NegotiatingBidder.java)
             * [RandomBidder](src/main/java/com/github/rinde/logistics/pdptw/mas/comm/RandomBidder.java)
   * Route finding:
         * [SolverRoutePlanner](src/main/java/com/github/rinde/logistics/pdptw/mas/route/SolverRoutePlanner.java)
         * [RtSolverRoutePlanner](src/main/java/com/github/rinde/logistics/pdptw/mas/route/RtSolverRoutePlanner.java) for real-time simulation.
         * [GotoClosestRoutePlanner](src/main/java/com/github/rinde/logistics/pdptw/mas/route/GotoClosestRoutePlanner.java)
         * [RandomRoutePlanner](src/main/java/com/github/rinde/logistics/pdptw/mas/route/RandomRoutePlanner.java)

