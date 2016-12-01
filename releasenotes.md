# Release notes

## v3.2.0
 * Compatible with RinSim 4.3.0
 * Added AuctionTimeStatsLogger and RoutePlannerStatsLogger
 * Reauctions can now be disabled in RtSolverBidder

## v3.1.0
 * Compatible with RinSim 4.2.0
 * Added simulated time versions of RtSolverBidder and RtSolverRoutePlanner
 * Computation time of OptaPlanner solver is now measurable
 * Bugfixes

## v3.0.0
 * Compatible with RinSim 4.1.1
 * Algorithms now respond to interrupts
 * Added real-time solvers
 * Added real-time auction implementation (DynCNET) + GUI support
 * Added integration with OptaPlanner library

## v2.0.0
 * Updated code to be compatible with RinSim 4.0.0

## v1.0.0
 * New release to emphasize stability

## v0.1.0
Initial release with support for:
* Agent communication types
	* Blackboard
	* Auction (single)
* Agent routeplanners
* Optimization algorithms
	* Cheapest insertion heuristic
	* 2-opt optimization