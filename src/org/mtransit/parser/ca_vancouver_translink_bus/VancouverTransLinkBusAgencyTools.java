package org.mtransit.parser.ca_vancouver_translink_bus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.Pair;
import org.mtransit.parser.SplitUtils;
import org.mtransit.parser.SplitUtils.RouteTripSpec;
import org.mtransit.parser.Utils;
import org.mtransit.parser.gtfs.data.GCalendar;
import org.mtransit.parser.gtfs.data.GCalendarDate;
import org.mtransit.parser.gtfs.data.GRoute;
import org.mtransit.parser.gtfs.data.GSpec;
import org.mtransit.parser.gtfs.data.GStop;
import org.mtransit.parser.gtfs.data.GTrip;
import org.mtransit.parser.gtfs.data.GTripStop;
import org.mtransit.parser.mt.data.MAgency;
import org.mtransit.parser.mt.data.MDirectionType;
import org.mtransit.parser.mt.data.MRoute;
import org.mtransit.parser.mt.data.MTrip;
import org.mtransit.parser.mt.data.MTripStop;

// http://www.translink.ca/en/Schedules-and-Maps/Developer-Resources.aspx
// http://www.translink.ca/en/Schedules-and-Maps/Developer-Resources/GTFS-Data.aspx
// http://mapexport.translink.bc.ca/current/google_transit.zip
// http://ns.translink.ca/gtfs/notifications.zip
// http://ns.translink.ca/gtfs/google_transit.zip
public class VancouverTransLinkBusAgencyTools extends DefaultAgencyTools {

	public static void main(String[] args) {
		if (args == null || args.length == 0) {
			args = new String[3];
			args[0] = "input/gtfs.zip";
			args[1] = "../../mtransitapps/ca-vancouver-translink-bus-android/res/raw/";
			args[2] = ""; // files-prefix
		}
		new VancouverTransLinkBusAgencyTools().start(args);
	}

	private HashSet<String> serviceIds;

	@Override
	public void start(String[] args) {
		System.out.printf("\nGenerating TransLink bus data...");
		long start = System.currentTimeMillis();
		this.serviceIds = extractUsefulServiceIds(args, this);
		super.start(args);
		System.out.printf("\nGenerating TransLink bus data... DONE in %s.\n", Utils.getPrettyDuration(System.currentTimeMillis() - start));
	}

	@Override
	public boolean excludeCalendar(GCalendar gCalendar) {
		if (this.serviceIds != null) {
			return excludeUselessCalendar(gCalendar, this.serviceIds);
		}
		return super.excludeCalendar(gCalendar);
	}

	@Override
	public boolean excludeCalendarDate(GCalendarDate gCalendarDates) {
		if (this.serviceIds != null) {
			return excludeUselessCalendarDate(gCalendarDates, this.serviceIds);
		}
		return super.excludeCalendarDate(gCalendarDates);
	}

	private static final List<String> EXCLUDE_RSN = Arrays.asList(new String[] { "980", "996", "997", "998", "999" });

	@Override
	public boolean excludeRoute(GRoute gRoute) {
		if (EXCLUDE_RSN.contains(gRoute.getRouteShortName())) {
			return true;
		}
		return super.excludeRoute(gRoute);
	}

	@Override
	public boolean excludeTrip(GTrip gTrip) {
		if (this.serviceIds != null) {
			return excludeUselessTrip(gTrip, this.serviceIds);
		}
		return super.excludeTrip(gTrip);
	}

	@Override
	public Integer getAgencyRouteType() {
		return MAgency.ROUTE_TYPE_BUS;
	}

	private static final Pattern DIGITS = Pattern.compile("[\\d]+");

	private static final String C = "C";
	private static final String N = "N";
	private static final String P = "P";

	private static final long RID_SW_C = 30000;
	private static final long RID_SW_N = 140000;
	private static final long RID_SW_P = 160000;

	@Override
	public long getRouteId(GRoute gRoute) {
		if (Utils.isDigitsOnly(gRoute.getRouteShortName())) {
			return Long.parseLong(gRoute.getRouteShortName()); // use route short name as route ID
		}
		Matcher matcher = DIGITS.matcher(gRoute.getRouteShortName());
		if (matcher.find()) {
			long id = Long.parseLong(matcher.group());
			if (gRoute.getRouteShortName().startsWith(C)) {
				return RID_SW_C + id;
			} else if (gRoute.getRouteShortName().startsWith(N)) {
				return RID_SW_N + id;
			} else if (gRoute.getRouteShortName().startsWith(P)) {
				return RID_SW_P + id;
			}
		}
		System.out.printf("\nUnexpected route ID %s\n", gRoute);
		System.exit(-1);
		return -1l;
	}

	@Override
	public String getRouteShortName(GRoute gRoute) {
		String routeShortName = gRoute.getRouteShortName(); // used by real-time API
		if (Utils.isDigitsOnly(routeShortName)) { // used by real-time API
			routeShortName = String.valueOf(Integer.valueOf(routeShortName)); // used by real-time API
		} // used by real-time API
		return routeShortName; // used by real-time API
	}

	@Override
	public String getRouteLongName(GRoute gRoute) {
		return cleanRouteLongName(gRoute.getRouteLongName());
	}

	private String cleanRouteLongName(String gRouteLongName) {
		gRouteLongName = gRouteLongName.toLowerCase(Locale.ENGLISH);
		gRouteLongName = CleanUtils.cleanSlashes(gRouteLongName);
		gRouteLongName = S_F_U.matcher(gRouteLongName).replaceAll(S_F_U_REPLACEMENT);
		gRouteLongName = U_B_C.matcher(gRouteLongName).replaceAll(U_B_C_REPLACEMENT);
		gRouteLongName = V_C_C.matcher(gRouteLongName).replaceAll(V_C_C_REPLACEMENT);
		return CleanUtils.cleanLabel(gRouteLongName);
	}

	private static final String AGENCY_COLOR_BLUE = "0761A5"; // BLUE (merge)

	private static final String AGENCY_COLOR = AGENCY_COLOR_BLUE;

	@Override
	public String getAgencyColor() {
		return AGENCY_COLOR;
	}

	private static final String NIGHT_BUS_COLOR = "062F53"; // DARK BLUE (from PDF map)
	private static final String B_LINE_BUS_COLOR = "F46717"; // ORANGE (from PDF map)

	private static final String B_LINE = "B-LINE";

	@Override
	public String getRouteColor(GRoute gRoute) {
		if (gRoute.getRouteShortName().startsWith(N)) {
			return NIGHT_BUS_COLOR;
		}
		if (gRoute.getRouteLongName().contains(B_LINE)) {
			return B_LINE_BUS_COLOR;
		}
		return null; // use agency color
	}

	private static final String EXCHANGE_SHORT = "Exch"; // like in GTFS
	private static final String UNIVERSITY_SHORT = "U";
	private static final String PORT_SHORT = "Pt"; // like GTFS & real-time API
	private static final String PORT_COQUITLAM_SHORT = "PoCo";
	private static final String COQUITLAM_SHORT = "Coq";
	private static final String ALBION = "Albion";
	private static final String HANEY_PLACE = "Haney Pl";
	private static final String STATION_SHORT = "Sta"; // see @CleanUtils
	private static final String MEADOWTOWN = "Meadowtown";
	private static final String POCO_STATION = PORT_COQUITLAM_SHORT + " " + STATION_SHORT;
	private static final String POCO_SOUTH = PORT_COQUITLAM_SHORT + " South";
	private static final String BELCARRA = "Belcarra";
	private static final String PORT_MOODY_STATION = PORT_SHORT + " Moody " + STATION_SHORT;
	private static final String LIONS_BAY = "Lions Bay";
	private static final String MAPLE_RDG_EAST = "Maple Rdg East";
	private static final String LADNER = "Ladner";
	private static final String LADNER_EXCH = LADNER + " " + EXCHANGE_SHORT;
	private static final String CARVOLTH = "Carvolth";
	private static final String CARVOLTH_EXCH = CARVOLTH + " " + EXCHANGE_SHORT;
	private static final String LOUGHEED_STATION = "Lougheed " + STATION_SHORT;
	private static final String GILBERT = "Gilbert";
	private static final String FIVE_RD = "Five Rd";
	private static final String BRIGHOUSE_STATION = "Brighouse " + STATION_SHORT;
	private static final String FOUR_RD = "Four Rd";
	private static final String BRIDGEPORT = "Bridgeport";
	private static final String BRIDGEPORT_STATION = BRIDGEPORT + " " + STATION_SHORT;
	private static final String RIVERSIDE = "Riverside";
	private static final String CRESCENT_BEACH = "Cr Beach";
	private static final String WHITBY_ESTATES = "Whitby Ests";
	private static final String WHITE_ROCK = "White Rock";
	private static final String NEW_WEST_STATION = "New West " + STATION_SHORT;
	private static final String NEWTON = "Newton";
	private static final String NEWTON_EXCH = NEWTON + " " + EXCHANGE_SHORT;
	private static final String SURREY_CENTRAL_STATION = "Surrey Ctrl " + STATION_SHORT;
	private static final String LANGLEY_CTR = "Langley Ctr";
	private static final String SOUTH_DELTA = "South Delta";
	private static final String BROOKSWOOD = "Brookswood";
	private static final String SUNBURY = "Sunbury";
	private static final String SPURAWAY = "Spuraway";
	private static final String DUNDARAVE = "Dundarave";
	private static final String BRITISH_PROPERTIES = "British Properties";
	private static final String GROUSE_MTN = "Grouse Mtn";
	private static final String UPPER_LONSDALE = "Upper Lonsdale";
	private static final String LONSDALE_QUAY = "Lonsdale Quay";
	private static final String UPPER_LYNN_VALLEY = "Upper Lynn Vly";
	private static final String BLUERIDGE = "Blueridge";
	private static final String VANCOUVER = "Vancouver";
	private static final String HORSESHOE_BAY = "Horseshoe Bay";
	private static final String CAPILANO_U = "Capilano " + UNIVERSITY_SHORT;
	private static final String PARK_ROYAL = "Pk Royal";
	private static final String RAILWAY = "Railway";
	private static final String _22ND_ST_STATION = "22nd St " + STATION_SHORT;
	private static final String DOWNTOWN = "Downtown";
	private static final String HASTINGS = "Hastings";
	private static final String POWELL = "Powell";
	private static final String UBC = "UBC";
	private static final String JOYCE_STATION = "Joyce " + STATION_SHORT;
	private static final String KOOTENAY_LOOP = "Kootenay Loop";
	private static final String BRENTWOOD_STATION = "Brentwood " + STATION_SHORT;
	private static final String BRAID_STATION = "Braid " + STATION_SHORT;
	private static final String COQ_STATION = COQUITLAM_SHORT + " " + STATION_SHORT;
	private static final String MACDONALD = "Macdonald";
	private static final String KNIGHT = "Knight";
	private static final String VICTORIA = "Victoria";
	private static final String LAKE_CITY_STATION = "Lk City " + STATION_SHORT;
	private static final String METROTOWN_STATION = "Metrotown " + STATION_SHORT;
	private static final String STANLEY_PARK = "Stanley Pk";
	private static final String BOUNDARY = "Boundary";
	private static final String GRANVILLE = "Granville";
	private static final String DUNBAR = "Dunbar";
	private static final String ARBUTUS = "Arbutus";
	private static final String SFU = "SFU";
	private static final String VCC = "VCC";
	private static final String EDMONDS_STATION = "Edmonds " + STATION_SHORT;
	private static final String _29TH_AVE_STATION = "29th Ave " + STATION_SHORT;
	private static final String _15TH_ST = "15th St";
	private static final String GUILDFORD = "Guildford";
	private static final String SEYMOUR = "Seymour";
	private static final String INGLEWOOD = "Inglewood";
	private static final String RIVERPORT = "Riverport";
	private static final String STEVESTON = "Steveston";

	private static HashMap<Long, RouteTripSpec> ALL_ROUTE_TRIPS2;
	static {
		HashMap<Long, RouteTripSpec> map2 = new HashMap<Long, RouteTripSpec>();
		map2.put(214l, new RouteTripSpec(214l, //
				0, MTrip.HEADSIGN_TYPE_STRING, BLUERIDGE, //
				1, MTrip.HEADSIGN_TYPE_STRING, VANCOUVER) //
				.addTripSort(0, //
						Arrays.asList(new String[] { "4154", "4113", "4116", "4118", "4067", "4163", "11553", "409" })) //
				.addTripSort(1, //
						Arrays.asList(new String[] { "409", "4143", "4068", "4069", "4070", "4071", "4072", "4154" })) //
				.compileBothTripSort());
		map2.put(606l, new RouteTripSpec(606l, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { "7572", "7582", "12126" })) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { "12128", "7460", "7572" })) //
				.compileBothTripSort());
		map2.put(608l, new RouteTripSpec(608l, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { "7602", "7478", "12130" })) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { "12130", "7621", "7602" })) //
				.compileBothTripSort());
		map2.put(804l, new RouteTripSpec(804l, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { "5743", "6003", "9452" })) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { "5918", "5933", "4845" })) //
				.compileBothTripSort());
		map2.put(807l, new RouteTripSpec(807l, //
				MDirectionType.NORTH.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.NORTH.getId(), //
				MDirectionType.SOUTH.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.SOUTH.getId()) //
				.addTripSort(MDirectionType.NORTH.intValue(), //
						Arrays.asList(new String[] { "7259", "7260", "7317", "7318", "7319", "7322", "7347", "9593", "9591", "11805" })) //
				.addTripSort(MDirectionType.SOUTH.intValue(), //
						Arrays.asList(new String[] { "9592", "7317", "7322", "5352", "7259" })) //
				.compileBothTripSort());
		map2.put(828l, new RouteTripSpec(828l, //
				MDirectionType.NORTH.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.NORTH.getId(), //
				MDirectionType.SOUTH.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.SOUTH.getId()) //
				.addTripSort(MDirectionType.NORTH.intValue(), //
						Arrays.asList(new String[] { "5768", "5840", "5852" })) //
				.addTripSort(MDirectionType.SOUTH.intValue(), //
						Arrays.asList(new String[] { "5852", "5861", "5769" })) //
				.compileBothTripSort());
		map2.put(855l, new RouteTripSpec(855l, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { "6425", "6153", "9062" })) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { "6133", "8806", "9132" })) //
				.compileBothTripSort());
		map2.put(861l, new RouteTripSpec(861l, //
				MDirectionType.NORTH.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.NORTH.getId(), //
				MDirectionType.SOUTH.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.SOUTH.getId()) //
				.addTripSort(MDirectionType.NORTH.intValue(), //
						Arrays.asList(new String[] { "3810", "3814", "3057" })) //
				.addTripSort(MDirectionType.SOUTH.intValue(), //
						Arrays.asList(new String[] { "3776", "3732", "3810" })) //
				.compileBothTripSort());
		map2.put(867l, new RouteTripSpec(867l, //
				MDirectionType.NORTH.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.NORTH.getId(), //
				MDirectionType.SOUTH.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.SOUTH.getId()) //
				.addTripSort(MDirectionType.NORTH.intValue(), //
						Arrays.asList(new String[] { "11940", "3317", "9337" })) //
				.addTripSort(MDirectionType.SOUTH.intValue(), //
						Arrays.asList(new String[] { "9336", "9650", "3221", "9337", "3394" })) //
				.compileBothTripSort());
		map2.put(880l, new RouteTripSpec(880l, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { "4473", "4070", "4144" })) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { "4113", "4118", "4473" })) //
				.compileBothTripSort());
		map2.put(881l, new RouteTripSpec(881l, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { "4439", "8018", "4224" })) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { "8018", "4430", "4438" })) //
				.compileBothTripSort());
		map2.put(895l, new RouteTripSpec(895l, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { "12084", "10935" })) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { "10935", "12084" })) //
				.compileBothTripSort());
		ALL_ROUTE_TRIPS2 = map2;
	}

	@Override
	public int compareEarly(long routeId, List<MTripStop> list1, List<MTripStop> list2, MTripStop ts1, MTripStop ts2, GStop ts1GStop, GStop ts2GStop) {
		if (ALL_ROUTE_TRIPS2.containsKey(routeId)) {
			return ALL_ROUTE_TRIPS2.get(routeId).compare(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop);
		}
		return super.compareEarly(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop);
	}

	@Override
	public ArrayList<MTrip> splitTrip(MRoute mRoute, GTrip gTrip, GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return ALL_ROUTE_TRIPS2.get(mRoute.getId()).getAllTrips();
		}
		return super.splitTrip(mRoute, gTrip, gtfs);
	}

	@Override
	public Pair<Long[], Integer[]> splitTripStop(MRoute mRoute, GTrip gTrip, GTripStop gTripStop, ArrayList<MTrip> splitTrips, GSpec routeGTFS) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return SplitUtils.splitTripStop(mRoute, gTrip, gTripStop, routeGTFS, ALL_ROUTE_TRIPS2.get(mRoute.getId()));
		}
		return super.splitTripStop(mRoute, gTrip, gTripStop, splitTrips, routeGTFS);
	}

	private static final String SPECIAL_ = "SPECIAL";

	@Override
	public void setTripHeadsign(MRoute mRoute, MTrip mTrip, GTrip gTrip, GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return; // split
		}
		if (mRoute.getId() == 96l) {
			if (gTrip.getTripHeadsign().toLowerCase(Locale.ENGLISH).contains("guildford")) {
				if (gTrip.getDirectionId() == 0) {
					mTrip.setHeadsignString(GUILDFORD, gTrip.getDirectionId());
					return;
				}
				System.out.printf("\nUnexpected route trip head sign for %s!\n", gTrip);
				System.exit(-1);
				return;
			}
		} else if (mRoute.getId() == 555l) {
			if (gTrip.getDirectionId() == 0) {
				mTrip.setHeadsignString(CARVOLTH_EXCH, gTrip.getDirectionId());
				return;
			} else if (gTrip.getDirectionId() == 1) {
				mTrip.setHeadsignString(LOUGHEED_STATION, gTrip.getDirectionId());
				return;
			}
		} else if (mRoute.getId() == 848l) {
			if (SPECIAL_.equalsIgnoreCase(gTrip.getTripHeadsign())) {
				mTrip.setHeadsignString(LOUGHEED_STATION, gTrip.getDirectionId());
				return;
			}
		} else if (mRoute.getId() == 863l) {
			if (SPECIAL_.equalsIgnoreCase(gTrip.getTripHeadsign())) {
				mTrip.setHeadsignDirection(MDirectionType.WEST);
				return;
			}
		} else if (mRoute.getId() == 865l) {
			if (SPECIAL_.equalsIgnoreCase(gTrip.getTripHeadsign())) {
				mTrip.setHeadsignString(HANEY_PLACE, mTrip.getHeadsignId());
				return;
			}
		}
		String gTripHeadsign = gTrip.getTripHeadsign();
		gTripHeadsign = STARTS_WITH_ROUTE.matcher(gTripHeadsign).replaceAll(StringUtils.EMPTY);
		Matcher matcherTO = TO.matcher(gTripHeadsign);
		if (matcherTO.find()) {
			String gTripHeadsignBeforeTO = gTripHeadsign.substring(0, matcherTO.start());
			String gTripHeadsignAfterTO = gTripHeadsign.substring(matcherTO.end());
			if (mRoute.getLongName().equalsIgnoreCase(gTripHeadsignBeforeTO)) {
				gTripHeadsign = gTripHeadsignAfterTO;
			} else if (mRoute.getLongName().equalsIgnoreCase(gTripHeadsignAfterTO)) {
				gTripHeadsign = gTripHeadsignBeforeTO;
			} else {
				gTripHeadsign = gTripHeadsignAfterTO;
			}
		}
		Matcher matcherVIA = VIA.matcher(gTripHeadsign);
		if (matcherVIA.find()) {
			String gTripHeadsignBeforeVIA = gTripHeadsign.substring(0, matcherVIA.start());
			String gTripHeadsignAfterVIA = gTripHeadsign.substring(matcherVIA.end());
			if (mRoute.getLongName().equalsIgnoreCase(gTripHeadsignBeforeVIA)) {
				gTripHeadsign = gTripHeadsignAfterVIA;
			} else if (mRoute.getLongName().equalsIgnoreCase(gTripHeadsignAfterVIA)) {
				gTripHeadsign = gTripHeadsignBeforeVIA;
			} else {
				gTripHeadsign = gTripHeadsignBeforeVIA;
			}
		}
		mTrip.setHeadsignString(cleanTripHeadsign(gTripHeadsign), gTrip.getDirectionId());
	}

	@Override
	public boolean mergeHeadsign(MTrip mTrip, MTrip mTripToMerge) {
		if (mTrip.getRouteId() == 4l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(POWELL, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 6l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 7l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(DUNBAR, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 8l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 9l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BOUNDARY, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 10l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(GRANVILLE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 14l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(HASTINGS, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 16l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(_29TH_AVE_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(ARBUTUS, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 17l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 19l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(METROTOWN_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(STANLEY_PARK, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 20l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(VICTORIA, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 22l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(KNIGHT, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(MACDONALD, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 25l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BRENTWOOD_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 27l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(KOOTENAY_LOOP, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(JOYCE_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 28l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(CAPILANO_U, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(JOYCE_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 41l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(JOYCE_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 49l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(METROTOWN_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 99l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BOUNDARY, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 101l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 110l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(LOUGHEED_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(METROTOWN_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 112l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(EDMONDS_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 123l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(BRENTWOOD_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 128l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BRAID_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 130l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(CAPILANO_U, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(METROTOWN_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 134l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(LAKE_CITY_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(BRENTWOOD_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 135l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(SFU, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 136l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(LOUGHEED_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(BRENTWOOD_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 151l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(COQ_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 155l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 159l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(BRAID_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 160l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(POCO_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 209l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 210l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(UPPER_LYNN_VALLEY, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 211l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(SEYMOUR, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 214l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 228l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(LONSDALE_QUAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 230l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(UPPER_LONSDALE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 236l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(GROUSE_MTN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 239l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(CAPILANO_U, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(PARK_ROYAL, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 240l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(_15TH_ST, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 242l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(UPPER_LONSDALE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 246l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(LONSDALE_QUAY, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 247l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(GROUSE_MTN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 250l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(HORSESHOE_BAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 252l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(INGLEWOOD, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 253l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 254l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BRITISH_PROPERTIES, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 255l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(CAPILANO_U, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(DUNDARAVE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 256l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(WHITBY_ESTATES, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(SPURAWAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 257l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(HORSESHOE_BAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 259l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(PARK_ROYAL, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 314l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(SUNBURY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 320l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(LANGLEY_CTR, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 321l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(NEW_WEST_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(WHITE_ROCK, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 323l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(NEWTON_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 329l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 335l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(NEWTON_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 337l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 341l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(GUILDFORD, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 351l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(CRESCENT_BEACH, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(CRESCENT_BEACH, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 388l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 401l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(RIVERPORT, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 403l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 404l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(FOUR_RD, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(BRIGHOUSE_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 405l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(FIVE_RD, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 407l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BRIDGEPORT, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(GILBERT, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 410l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(RAILWAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 501l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(LANGLEY_CTR, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 502l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BROOKSWOOD, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 503l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 555l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(CARVOLTH_EXCH, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(LOUGHEED_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 601l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(SOUTH_DELTA, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 603l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 604l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 640l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(LADNER_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 701l) {
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(MAPLE_RDG_EAST, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 848l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(LOUGHEED_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 863l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignDirection(MDirectionType.WEST);
				return true;
			}
		} else if (mTrip.getRouteId() == 865l) {
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(HANEY_PLACE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 12l) { // C12
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(LIONS_BAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 25l) { // C25
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BELCARRA, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 26l) { // C26
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(BELCARRA, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 28l) { // C28
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(PORT_MOODY_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 36l) { // C36
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(POCO_SOUTH, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 37l) { // C37
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(RIVERSIDE, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(POCO_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 43l) { // C43
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(MEADOWTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 44l) { // C44
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(MEADOWTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 45l) { // C45
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(HANEY_PLACE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 46l) { // C46
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(ALBION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 93l) { // C93
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(RIVERPORT, mTrip.getHeadsignId());
				return true;
			} else if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(STEVESTON, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_N + 10l) { // N10
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_N + 19l) { // N19
			if (mTrip.getHeadsignId() == 0) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_N + 24l) { // N24
			if (mTrip.getHeadsignId() == 1) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		}
		return super.mergeHeadsign(mTrip, mTripToMerge);
	}

	private static final Pattern TO = Pattern.compile("((^|\\W){1}(to)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final Pattern VIA = Pattern.compile("((^|\\W){1}(via)(\\W|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_QUOTE = Pattern.compile("(^\")", Pattern.CASE_INSENSITIVE);

	private static final Pattern ENDS_WITH_QUOTE = Pattern.compile("(\"[;]?[\\s]?$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_ROUTE = Pattern.compile("(^[A-Z]{0,1}[0-9]{1,3}[\\s]+{1})", Pattern.CASE_INSENSITIVE);

	private static final String SPACE = " ";

	private static final Pattern EXPRESS = Pattern.compile("(\\-?( express|express )\\-?)", Pattern.CASE_INSENSITIVE);

	private static final Pattern SPECIAL = Pattern.compile("(\\-?( special|special )\\-?)", Pattern.CASE_INSENSITIVE);

	private static final Pattern ONLY = Pattern.compile("(\\-?( only|only )\\-?)", Pattern.CASE_INSENSITIVE);

	private static final Pattern UNIVERSITY = Pattern.compile("((^|\\W){1}(university)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String UNIVERSITY_REPLACEMENT = "$2" + UNIVERSITY_SHORT + "$4";

	private static final Pattern PORT_COQUITLAM = Pattern.compile("((^|\\W){1}(port coquitlam|pt coquitlam|poco)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String PORT_COQUITLAM_REPLACEMENT = "$2" + PORT_COQUITLAM_SHORT + "$4";

	private static final Pattern COQUITLAM = Pattern.compile("((^|\\W){1}(coquitlam|coq)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String COQUITLAM_REPLACEMENT = "$2" + COQUITLAM_SHORT + "$4";

	private static final Pattern STATION = Pattern.compile("((^|\\W){1}(stn|sta|station)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String STATION_REPLACEMENT = "$2" + STATION_SHORT + "$4";

	private static final Pattern PORT = Pattern.compile("((^|\\W){1}(port)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String PORT_REPLACEMENT = "$2" + PORT_SHORT + "$4";

	private static final Pattern NIGHTBUS = Pattern.compile("((^|\\s){1}(nightbus)(\\s|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern EXCHANGE = Pattern.compile("((^|\\s){1}(exchange|exch)(\\s|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String EXCHANGE_REPLACEMENT = "$2" + EXCHANGE_SHORT + "$4";

	private static final Pattern ENDS_WITH_B_LINE = Pattern.compile("((^|\\s){1}(\\- )?(b\\-line)(\\s|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern U_B_C = Pattern.compile("((^|\\W){1}(ubc|u b c)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String U_B_C_REPLACEMENT = "$2" + UBC + "$4";

	private static final Pattern S_F_U = Pattern.compile("((^|\\W){1}(sfu|s f u)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String S_F_U_REPLACEMENT = "$2" + SFU + "$4";

	private static final Pattern V_C_C = Pattern.compile("((^|\\W){1}(vcc|v c c)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String V_C_C_REPLACEMENT = "$2" + VCC + "$4";

	private static final Pattern REMOVE_DASH = Pattern.compile("(^(\\s)*\\-(\\s)*|(\\s)*\\-(\\s)*$)", Pattern.CASE_INSENSITIVE);

	@Override
	public String cleanTripHeadsign(String tripHeadsign) {
		tripHeadsign = tripHeadsign.toLowerCase(Locale.ENGLISH);
		tripHeadsign = CleanUtils.cleanSlashes(tripHeadsign);
		tripHeadsign = STARTS_WITH_QUOTE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = ENDS_WITH_QUOTE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = STARTS_WITH_ROUTE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = ENDS_WITH_B_LINE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = CleanUtils.CLEAN_AND.matcher(tripHeadsign).replaceAll(CleanUtils.CLEAN_AND_REPLACEMENT);
		tripHeadsign = CleanUtils.CLEAN_AT.matcher(tripHeadsign).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		tripHeadsign = AT_LIKE.matcher(tripHeadsign).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		tripHeadsign = EXCHANGE.matcher(tripHeadsign).replaceAll(EXCHANGE_REPLACEMENT);
		tripHeadsign = S_F_U.matcher(tripHeadsign).replaceAll(S_F_U_REPLACEMENT);
		tripHeadsign = U_B_C.matcher(tripHeadsign).replaceAll(U_B_C_REPLACEMENT);
		tripHeadsign = V_C_C.matcher(tripHeadsign).replaceAll(V_C_C_REPLACEMENT);
		tripHeadsign = UNIVERSITY.matcher(tripHeadsign).replaceAll(UNIVERSITY_REPLACEMENT);
		tripHeadsign = PORT_COQUITLAM.matcher(tripHeadsign).replaceAll(PORT_COQUITLAM_REPLACEMENT);
		tripHeadsign = COQUITLAM.matcher(tripHeadsign).replaceAll(COQUITLAM_REPLACEMENT);
		tripHeadsign = STATION.matcher(tripHeadsign).replaceAll(STATION_REPLACEMENT);
		tripHeadsign = PORT.matcher(tripHeadsign).replaceAll(PORT_REPLACEMENT);
		tripHeadsign = NIGHTBUS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = EXPRESS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = SPECIAL.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = ONLY.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = REMOVE_DASH.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = CleanUtils.removePoints(tripHeadsign);
		tripHeadsign = CleanUtils.cleanNumbers(tripHeadsign);
		tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
		return CleanUtils.cleanLabel(tripHeadsign);
	}

	private static final Pattern AT_LIKE = Pattern.compile("((^|\\W){1}(fs|ns)(\\W|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern FLAG_STOP = Pattern.compile("((^flagstop)[\\s]*(.*$))", Pattern.CASE_INSENSITIVE);
	private static final String FLAG_STOP_REPLACEMENT = "$3 ($2)";

	private static final Pattern BOUND = Pattern.compile("((^|\\W){1}(eb|wb|sb|nb)(\\W|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern UNLOADING = Pattern.compile("(unloading( only)?$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern ENDS_WITH_DASHES = Pattern.compile("([\\-]+$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_AT = Pattern.compile("(^@ )", Pattern.CASE_INSENSITIVE);

	@Override
	public String cleanStopName(String gStopName) {
		gStopName = gStopName.toLowerCase(Locale.ENGLISH);
		gStopName = CleanUtils.cleanSlashes(gStopName);
		gStopName = BOUND.matcher(gStopName).replaceAll(SPACE);
		gStopName = CleanUtils.SAINT.matcher(gStopName).replaceAll(CleanUtils.SAINT_REPLACEMENT);
		gStopName = AT_LIKE.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		gStopName = CleanUtils.CLEAN_AT.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		gStopName = CleanUtils.CLEAN_AND.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AND_REPLACEMENT);
		gStopName = FLAG_STOP.matcher(gStopName).replaceAll(FLAG_STOP_REPLACEMENT);
		gStopName = UNLOADING.matcher(gStopName).replaceAll(StringUtils.EMPTY);
		gStopName = ENDS_WITH_DASHES.matcher(gStopName).replaceAll(StringUtils.EMPTY);
		gStopName = EXCHANGE.matcher(gStopName).replaceAll(EXCHANGE_REPLACEMENT);
		gStopName = STATION.matcher(gStopName).replaceAll(STATION_REPLACEMENT);
		gStopName = S_F_U.matcher(gStopName).replaceAll(S_F_U_REPLACEMENT);
		gStopName = U_B_C.matcher(gStopName).replaceAll(U_B_C_REPLACEMENT);
		gStopName = V_C_C.matcher(gStopName).replaceAll(V_C_C_REPLACEMENT);
		gStopName = STARTS_WITH_AT.matcher(gStopName).replaceAll(StringUtils.EMPTY);
		gStopName = CleanUtils.cleanStreetTypes(gStopName);
		return CleanUtils.cleanLabel(gStopName);
	}

	@Override
	public int getStopId(GStop gStop) {
		if (!StringUtils.isEmpty(gStop.getStopCode()) && Utils.isDigitsOnly(gStop.getStopCode())) {
			return Integer.parseInt(gStop.getStopCode()); // using stop code as stop ID
		}
		return 1000000 + Integer.parseInt(gStop.getStopId());
	}
}
