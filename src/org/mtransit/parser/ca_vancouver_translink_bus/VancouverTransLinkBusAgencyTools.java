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

	private static final List<String> EXCLUDE_RSN = Arrays.asList(new String[] { //
			"980", // CANADA LINE SKYTRAIN
					"991", // MILLENNIUM SKYTRAIN
					"992", // EXPO SKYTRAIN
					"997", // WEST COAST EXPRESS
					"998", // SEABUS
			});

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

	private static final String SPACE = " ";
	private static final String SLASH = " / ";
	private static final String DASH = " - ";
	private static final String EXCHANGE_SHORT = "Exch"; // like in GTFS
	private static final String UNIVERSITY_SHORT = "U";
	private static final String PORT_SHORT = "Pt"; // like GTFS & real-time API
	private static final String PORT_COQUITLAM_SHORT = "PoCo";
	private static final String COQUITLAM_SHORT = "Coq";
	private static final String STATION_SHORT = "Sta"; // see @CleanUtils
	private static final String VALLEY_SHORT = "Vly";
	private static final String CENTER_SHORT = "Ctr";
	private static final String GARDEN_SHORT = "Gdn";
	private static final String MEADOWS_SHORT = "Mdws";
	private static final String CENTRAL_SHORT = "Ctrl";
	private static final String STREET_SHORT = "St";
	private static final String HILL_SHORT = "Hl";
	private static final String SOUTH = "South";
	private static final String PHIBBS_EXCHANGE = "Phibbs " + EXCHANGE_SHORT;
	private static final String ALBION = "Albion";
	private static final String HANEY = "Haney";
	private static final String HANEY_PLACE = HANEY + " Pl";
	private static final String PT_HANEY_STATION = "Pt " + HANEY + SPACE + STATION_SHORT;
	private static final String SPERLING_STATION = "Sperling " + STATION_SHORT;
	private static final String MAIN_MARINE_DR_STATION = "Main-Marine Dr " + STATION_SHORT;
	private static final String GILMORRE_STATION = "Gilmore " + STATION_SHORT;
	private static final String NANAIMO_STATION = "Nanaimo " + STATION_SHORT;
	private static final String MEADOWTOWN = "Meadowtown";
	private static final String BELCARRA = "Belcarra";
	private static final String LIONS_BAY = "Lions Bay";
	private static final String SCOTTSDALE = "Scottsdale";
	private static final String SCOTTSDALE_EXCH = SCOTTSDALE + SPACE + EXCHANGE_SHORT;
	private static final String MAPLE_RIDGE = "Maple Rdg";
	private static final String MAPLE_RIDGE_E = MAPLE_RIDGE + " E";
	private static final String MAPLE_RIDGE_EAST = MAPLE_RIDGE + " East";
	private static final String LADNER = "Ladner";
	private static final String LADNER_EXCH = LADNER + " " + EXCHANGE_SHORT;
	private static final String CARVOLTH = "Carvolth";
	private static final String CARVOLTH_EXCH = CARVOLTH + " " + EXCHANGE_SHORT;
	private static final String LOUGHEED_STATION = "Lougheed " + STATION_SHORT;
	private static final String GILBERT = "Gilbert";
	private static final String FIVE_RD = "Five Rd";
	private static final String BRIGHOUSE_STATION = "Brighouse " + STATION_SHORT;
	private static final String BRIDGEPORT = "Bridgeport";
	private static final String BRIDGEPORT_STATION = BRIDGEPORT + " " + STATION_SHORT;
	private static final String RIVERSIDE = "Riverside";
	private static final String CRESCENT_BEACH = "Cr Beach";
	private static final String WHITE_ROCK = "White Rock";
	private static final String NEW_WEST_STATION = "New West " + STATION_SHORT;
	private static final String NEWTON = "Newton";
	private static final String NEWTON_EXCH = NEWTON + " " + EXCHANGE_SHORT;
	private static final String SURREY_SHORT = "Sry";
	private static final String SURREY_CENTRAL_STATION = SURREY_SHORT + SPACE + CENTRAL_SHORT + " " + STATION_SHORT;
	private static final String COQUITLAM_CENTRAL_STATION = COQUITLAM_SHORT + " " + CENTRAL_SHORT + " " + STATION_SHORT;
	private static final String LANGLEY_CTR = "Langley " + CENTER_SHORT;
	private static final String SOUTH_DELTA = SOUTH + " Delta";
	private static final String SOUTH_HANEY = SOUTH + " Haney";
	private static final String SUNBURY = "Sunbury";
	private static final String SPURAWAY = "Spuraway";
	private static final String DUNDARAVE = "Dundarave";
	private static final String BRITISH_PROPERTIES = "British Properties";
	private static final String GROUSE_MTN = "Grouse Mtn";
	private static final String CAPILANO = "Capilano";
	private static final String CAPILANO_U = CAPILANO + SPACE + UNIVERSITY_SHORT;
	private static final String UPPER = "Upper";
	private static final String UPPER_CAPILANO = UPPER + SPACE + CAPILANO;
	private static final String UPPER_LONSDALE = UPPER + " Lonsdale";
	private static final String LYNN_VALLEY = "Lynn " + VALLEY_SHORT;
	private static final String UPPER_LYNN_VALLEY = UPPER + SPACE + LYNN_VALLEY;
	private static final String LONSDALE_QUAY = "Lonsdale Quay";
	private static final String BLUERIDGE = "Blueridge";
	private static final String VANCOUVER = "Vancouver";
	private static final String HORSESHOE = "Horseshoe";
	private static final String HORSESHOE_BAY = HORSESHOE + " Bay";
	private static final String HORSESHOE_WAY = HORSESHOE + " Way";
	private static final String PARK_ROYAL = "Pk Royal";
	private static final String RAILWAY = "Railway";
	private static final String _22ND_ST_STATION = "22nd St " + STATION_SHORT;
	private static final String DOWNTOWN = "Downtown";
	private static final String HASTINGS = "Hastings";
	private static final String POWELL = "Powell";
	private static final String UBC = "UBC";
	private static final String JOYCE = "Joyce";
	private static final String JOYCE_STATION = JOYCE + " " + STATION_SHORT;
	private static final String KOOTENAY_LOOP = "Kootenay Loop";
	private static final String BRENTWOOD_STATION = "Brentwood " + STATION_SHORT;
	private static final String BRAID_STATION = "Braid " + STATION_SHORT;
	private static final String MACDONALD = "Macdonald";
	private static final String KNIGHT = "Knight";
	private static final String IOCO = "Ioco";
	private static final String CITY_HALL = "City Hall";
	private static final String _16_AVE = "16 Ave";
	private static final String KNIGHT_STREET = KNIGHT + SPACE + STREET_SHORT;
	private static final String VICTORIA = "Victoria";
	private static final String VICTORIA_HILL = VICTORIA + SPACE + HILL_SHORT;
	private static final String LAKE_CITY_STATION = "Lk City " + STATION_SHORT;
	private static final String METROTOWN_STATION = "Metrotown " + STATION_SHORT;
	private static final String STANLEY_PARK = "Stanley Pk";
	private static final String BOUNDARY = "Boundary";
	private static final String BOUNDARY_BAY = BOUNDARY + " Bay";
	private static final String BOUNDARY_RD = BOUNDARY + " Rd";
	private static final String GRANVILLE = "Granville";
	private static final String DUNBAR = "Dunbar";
	private static final String DUNBAR_LOOP = DUNBAR + " Loop";
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
	private static final String BURQUITLAM_STATION = "Burquitlam " + STATION_SHORT;
	private static final String WILLINGDON = "Willingdon";
	private static final String CROWN = "Crown";
	private static final String _41ST_OAK = "41st & Oak";
	private static final String BCIT = "Bcit";
	private static final String _54TH_AVE = "54th Ave";
	private static final String _41ST_AVE = "41st Ave";
	private static final String BROADWAY = "Broadway";
	private static final String MARPOLE = "Marpole";
	private static final String _63RD = "63rd";
	private static final String DAVIE = "Davie";
	private static final String ROBSON = "Robson";
	private static final String COMM_L_BDWAY_STATION = "Comm'l-Bdway " + STATION_SHORT;
	private static final String MAIN = "Main";
	private static final String OAK = "Oak";
	private static final String ALMA = "Alma";
	private static final String _41ST = "41st";
	private static final String BURRARD_STATION = "Burrard " + STATION_SHORT;
	private static final String BLANCA = "Blanca";
	private static final String SPECIAL = "Special";
	private static final String GRAND_BLVD = "Grand Blvd";
	private static final String _15_ST = "15 St";
	private static final String VILLAGE_SHORT = "Vlg";
	private static final String EDGEMONT_VILLAGE = "Edgemont " + VILLAGE_SHORT;
	private static final String AND = " & ";
	private static final String HEIGHTS_SHORT = "Hts";
	private static final String PEMBERTON_HEIGHTS = "Pemberton " + HEIGHTS_SHORT;
	private static final String MISSION_CITY_STATION = "Mission City " + STATION_SHORT;
	private static final String S_DELTA_EXCH = "S Delta " + EXCHANGE_SHORT;
	private static final String STEVESTON_HWY = "Steveston Hwy";
	private static final String TSAWW_HEIGTHS = "Tsaww " + HEIGHTS_SHORT;
	private static final String PT_MANN_EXP = "Pt Mann Exp";
	private static final String WILLOWBROOK = "Willowbrook";
	private static final String WALNUT_GROVE = "Walnut Grv";
	private static final String QUEENSBORO = "Queensboro";
	private static final String HAMILTON_PARK = "Hamilton Pk";
	private static final String _5_RD = "5 Rd";
	private static final String _132ND = "132nd";
	private static final String _76TH = "76th";
	private static final String FLEETWOOD = "Fleetwood";
	private static final String _96_AVE = "96 Ave";
	private static final String SCOTT = "Scott";
	private static final String MALL = "Mall";
	private static final String WEST_VAN_SECONDARY = "West Van Secondary";
	private static final String WEST_BAY = "West Bay";
	private static final String _25TH = "25th";
	private static final String MARINE = "Marine";
	private static final String HIGHLAND = "Highland";
	private static final String SCHOOL = "School";
	private static final String BRUNSWICK = "Brunswick";
	private static final String BLUEWATER = "Bluewater";
	private static final String QUAYSIDE = "Quayside";
	private static final String MAPLE_MEADOWS_STATION = "Maple " + MEADOWS_SHORT + SPACE + STATION_SHORT;
	private static final String ST_DAVIDS = "St Davids";
	private static final String _3RD = "3rd";
	private static final String _71 = "71";
	private static final String KINGSWOOD = "Kingswood";

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
		map2.put(555L, new RouteTripSpec(555L, //
				0, MTrip.HEADSIGN_TYPE_STRING, CARVOLTH_EXCH, //
				1, MTrip.HEADSIGN_TYPE_STRING, LOUGHEED_STATION) //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"8672", // LOUGHEED STN BAY 8
								"11805", // CARVOLTH EXCHANGE BAY 1
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"11799", // CARVOLTH EXCHANGE BAY 9
								"8691", // LOUGHEED STN BAY 6
						})) //
				.compileBothTripSort());
		map2.put(606l, new RouteTripSpec(606l, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { "7572", "7582", "7591" /* "12126" */})) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { /* "12128" */"7591", "7460", "7572" })) //
				.compileBothTripSort());
		map2.put(608l, new RouteTripSpec(608l, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { "7602", "7478", "7591" /* "12130" */})) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { /* "12130" */"7591", "7621", "7602" })) //
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
		map2.put(848L, new RouteTripSpec(848L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "", //
				1, MTrip.HEADSIGN_TYPE_STRING, LOUGHEED_STATION) //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						/* no stops */
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"3221", // WB SAINT JOHNS ST FS BARNET HWY
								"8691", // LOUGHEED STN BAY 6
						})) //
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
		map2.put(863L, new RouteTripSpec(863L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Arch Carney", //
				1, MTrip.HEADSIGN_TYPE_STRING, "") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"3823", // EB RIVERWOOD GATE AT TERRY FOX SECONDARY
								"3868", // SB SHAUGHNESSY ST FS PRAIRIE AVE
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						/* no stops */
						})) //
				.compileBothTripSort());
		map2.put(865L, new RouteTripSpec(865L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "", //
				1, MTrip.HEADSIGN_TYPE_STRING, HANEY_PLACE) //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						/* no stops */
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"9635", // WB 104 AVE FS 245 ST
								"10891", // HANEY PLACE BAY 7 UNLOADING ONLY
						})) //
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
		map2.put(RID_SW_C + 19l, new RouteTripSpec(RID_SW_C + 19l, // C19
				0, MTrip.HEADSIGN_TYPE_STRING, ALMA, //
				1, MTrip.HEADSIGN_TYPE_STRING, "Spanish Banks") //
				// MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				// MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(0, //
						Arrays.asList(new String[] { "11002", "355", "674" })) //
				.addTripSort(1, //
						Arrays.asList(new String[] { "674", "599", "11002" })) //
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

	@Override
	public void setTripHeadsign(MRoute mRoute, MTrip mTrip, GTrip gTrip, GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return; // split
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
		if (mTrip.getRouteId() == 2l) {
			if (Arrays.asList( //
					BURRARD_STATION, //
					DOWNTOWN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					MACDONALD + DASH + _16_AVE, //
					MACDONALD //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MACDONALD, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 3l) {
			if (Arrays.asList( //
					MAIN_MARINE_DR_STATION, //
					MAIN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MAIN_MARINE_DR_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 4l) {
			if (Arrays.asList( //
					DOWNTOWN, //
					POWELL //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(POWELL, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					BLANCA, //
					UBC //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 7l) {
			if (Arrays.asList( //
					DOWNTOWN, //
					HASTINGS, //
					DUNBAR //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DUNBAR, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 8l) {
			if (Arrays.asList( //
					_41ST, //
					DOWNTOWN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 9l) {
			if (Arrays.asList( //
					COMM_L_BDWAY_STATION, //
					MAIN, //
					BOUNDARY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BOUNDARY, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					ALMA, //
					GRANVILLE, //
					OAK //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(ALMA, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 10l) {
			if (Arrays.asList( //
					_63RD, //
					DAVIE, //
					ROBSON, //
					DOWNTOWN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					MARPOLE, //
					GRANVILLE //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(GRANVILLE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 14l) {
			if (Arrays.asList( //
					DOWNTOWN, //
					GRANVILLE, //
					HASTINGS //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(HASTINGS, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					BLANCA, //
					UBC //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 16l) {
			if (Arrays.asList( //
					DOWNTOWN, //
					_29TH_AVE_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_29TH_AVE_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					HASTINGS, //
					ARBUTUS //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(ARBUTUS, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 17l) {
			if (Arrays.asList( //
					BROADWAY, //
					SPECIAL, //
					DOWNTOWN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 19l) {
			if (Arrays.asList( //
					GRANVILLE, //
					METROTOWN_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(METROTOWN_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					DOWNTOWN, //
					JOYCE, //
					STANLEY_PARK //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(STANLEY_PARK, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 20l) {
			if (Arrays.asList( //
					_41ST_AVE, //
					BROADWAY, //
					DOWNTOWN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					_41ST_AVE, //
					_54TH_AVE, //
					VICTORIA //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VICTORIA, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 22l) {
			if (Arrays.asList( //
					_63RD, //
					KNIGHT //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(KNIGHT, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 25l) {
			if (Arrays.asList( //
					BCIT, //
					NANAIMO_STATION, //
					BRENTWOOD_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRENTWOOD_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					GRANVILLE, //
					UBC //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 27l) {
			if (Arrays.asList( //
					JOYCE_STATION, //
					KOOTENAY_LOOP //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(KOOTENAY_LOOP, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 28l) {
			if (Arrays.asList( //
					KOOTENAY_LOOP, //
					PHIBBS_EXCHANGE //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(PHIBBS_EXCHANGE, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					GILMORRE_STATION, //
					KOOTENAY_LOOP, //
					JOYCE_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(JOYCE_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 41l) {
			if (Arrays.asList( //
					_41ST_OAK, //
					SPECIAL, //
					JOYCE_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(JOYCE_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					_41ST_OAK, //
					CROWN, //
					DUNBAR, //
					UBC //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 49l) {
			if (Arrays.asList( //
					GRANVILLE, //
					DUNBAR_LOOP, //
					UBC //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 95l) {
			if (Arrays.asList( //
					WILLINGDON, //
					SFU //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SFU, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 99l) {
			if (Arrays.asList( //
					COMM_L_BDWAY_STATION, //
					BOUNDARY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BOUNDARY, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					ALMA, //
					UBC //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 100l) {
			if (Arrays.asList( //
					KNIGHT_STREET, //
					_22ND_ST_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 101l) {
			if (Arrays.asList( //
					SPECIAL, //
					_22ND_ST_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 110l) {
			if (Arrays.asList( //
					CITY_HALL, //
					LOUGHEED_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LOUGHEED_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					SPERLING_STATION, //
					SPECIAL, //
					METROTOWN_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(METROTOWN_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 112l) {
			if (Arrays.asList( //
					METROTOWN_STATION, //
					EDMONDS_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(EDMONDS_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 130l) {
			if (Arrays.asList( //
					HASTINGS, //
					KOOTENAY_LOOP, //
					PHIBBS_EXCHANGE //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(PHIBBS_EXCHANGE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 134l) {
			if (Arrays.asList( //
					SPECIAL, //
					LAKE_CITY_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LAKE_CITY_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					SPECIAL, //
					BRENTWOOD_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRENTWOOD_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 136l) {
			if (Arrays.asList( //
					SPECIAL, //
					LOUGHEED_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LOUGHEED_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					SPECIAL, //
					BRENTWOOD_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRENTWOOD_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 151l) {
			if (Arrays.asList( //
					SPECIAL, //
					COQUITLAM_CENTRAL_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(COQUITLAM_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					SPECIAL, //
					BURQUITLAM_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BURQUITLAM_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 155l) {
			if (Arrays.asList( //
					SPECIAL, //
					_22ND_ST_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 159l) {
			if (Arrays.asList( //
					SPECIAL, //
					BRAID_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRAID_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 172l) {
			if (Arrays.asList( //
					SPECIAL, //
					RIVERSIDE //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(RIVERSIDE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 181l) {
			if (Arrays.asList( //
					IOCO, //
					BELCARRA //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BELCARRA, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 209l) {
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					VANCOUVER //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 210l) {
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					UPPER_LYNN_VALLEY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UPPER_LYNN_VALLEY, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					VANCOUVER //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 211l) {
			if (Arrays.asList( //
					SPECIAL, //
					SEYMOUR //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SEYMOUR, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					VANCOUVER //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 228l) {
			if (Arrays.asList( //
					GRAND_BLVD + AND + _15_ST, //
					LONSDALE_QUAY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LONSDALE_QUAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 230l) {
			if (Arrays.asList( //
					STREET_SHORT, //
					UPPER_LONSDALE //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UPPER_LONSDALE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 232l) {
			if (Arrays.asList( //
					EDGEMONT_VILLAGE, //
					GROUSE_MTN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(GROUSE_MTN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 236l) {
			if (Arrays.asList( //
					PEMBERTON_HEIGHTS, //
					GROUSE_MTN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(GROUSE_MTN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 239l) {
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					CAPILANO_U //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(CAPILANO_U, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					LONSDALE_QUAY, //
					PHIBBS_EXCHANGE, //
					PARK_ROYAL //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(PARK_ROYAL, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 240l) {
			if (Arrays.asList( //
					SCHOOL, //
					_15TH_ST //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_15TH_ST, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					_15TH_ST, //
					VANCOUVER //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 242l) {
			if (Arrays.asList( //
					LONSDALE_QUAY, //
					LYNN_VALLEY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LYNN_VALLEY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 246l) {
			if (Arrays.asList( //
					HIGHLAND, //
					LONSDALE_QUAY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LONSDALE_QUAY, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					HIGHLAND, //
					MARINE + AND + GARDEN_SHORT, //
					MARINE + AND + CAPILANO, //
					VANCOUVER //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 247l) {
			if (Arrays.asList( //
					UPPER_CAPILANO, //
					GROUSE_MTN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(GROUSE_MTN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 250l) {
			if (Arrays.asList( //
					PARK_ROYAL, //
					SPECIAL, //
					VANCOUVER //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					_25TH + AND + MARINE, //
					WEST_BAY, //
					PARK_ROYAL, //
					SCHOOL, //
					HORSESHOE_BAY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(HORSESHOE_BAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 252l) {
			if (Arrays.asList( //
					WEST_VAN_SECONDARY, //
					INGLEWOOD //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(INGLEWOOD, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 253l) {
			if (Arrays.asList( //
					PARK_ROYAL, //
					VANCOUVER //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 254l) {
			if (Arrays.asList( //
					SCHOOL, //
					BRITISH_PROPERTIES //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRITISH_PROPERTIES, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					SCHOOL, //
					PARK_ROYAL, //
					VANCOUVER //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 255l) {
			if (Arrays.asList( //
					PARK_ROYAL, //
					CAPILANO_U //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(CAPILANO_U, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					PARK_ROYAL, //
					CAPILANO + SPACE + MALL, //
					_25TH + AND + MARINE, //
					DUNDARAVE //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DUNDARAVE, mTrip.getHeadsignId()); // _25TH + AND + MARINE
				return true;
			}
		} else if (mTrip.getRouteId() == 256l) {
			if (Arrays.asList( //
					PARK_ROYAL, //
					SPURAWAY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SPURAWAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 257l) {
			if (Arrays.asList( //
					PARK_ROYAL, //
					SPECIAL, //
					VANCOUVER //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					PARK_ROYAL, //
					HORSESHOE_BAY + AND + LIONS_BAY, //
					HORSESHOE_BAY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(HORSESHOE_BAY, mTrip.getHeadsignId()); //
				return true;
			}
		} else if (mTrip.getRouteId() == 259l) {
			if (Arrays.asList( //
					HORSESHOE_BAY, //
					PARK_ROYAL //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(PARK_ROYAL, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 314l) {
			if (Arrays.asList( //
					SPECIAL, //
					SURREY_CENTRAL_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					SCOTT + AND + _96_AVE, //
					SUNBURY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SUNBURY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 320l) {
			if (Arrays.asList( //
					FLEETWOOD, //
					LANGLEY_CTR //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LANGLEY_CTR, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 321l) {
			if (Arrays.asList( //
					SURREY_CENTRAL_STATION, //
					NEW_WEST_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(NEW_WEST_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					NEWTON_EXCH, //
					WHITE_ROCK + SPACE + CENTER_SHORT, //
					WHITE_ROCK + SPACE + SOUTH, //
					WHITE_ROCK //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(WHITE_ROCK, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 323l) {
			if (Arrays.asList( //
					_76TH + AND + _132ND, //
					NEWTON_EXCH //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(NEWTON_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 329l) {
			if (Arrays.asList( //
					SCOTT + AND + _96_AVE, //
					SURREY_CENTRAL_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 335l) {
			if (Arrays.asList( //
					GUILDFORD, //
					NEWTON_EXCH //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(NEWTON_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 337l) {
			if (Arrays.asList( //
					GUILDFORD, //
					SURREY_CENTRAL_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 351l) {
			if (Arrays.asList( //
					WHITE_ROCK + SPACE + CENTER_SHORT, //
					CRESCENT_BEACH //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(CRESCENT_BEACH, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 388l) {
			if (Arrays.asList( //
					SPECIAL, //
					_22ND_ST_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 401l) {
			if (Arrays.asList( //
					HORSESHOE_WAY, //
					RIVERPORT //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(RIVERPORT, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 403l) {
			if (Arrays.asList( //
					FIVE_RD, //
					BRIDGEPORT_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 404l) {
			if (Arrays.asList( //
					STEVESTON + AND + _5_RD, //
					BRIGHOUSE_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIGHOUSE_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 405l) {
			if (Arrays.asList( //
					BRIGHOUSE_STATION, //
					FIVE_RD //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(FIVE_RD, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 407l) {
			if (Arrays.asList( //
					BRIGHOUSE_STATION, //
					BRIDGEPORT //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIDGEPORT, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					BRIGHOUSE_STATION, //
					GILBERT //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(GILBERT, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 410l) {
			if (Arrays.asList( //
					BRIGHOUSE_STATION, //
					BOUNDARY_RD, //
					_22ND_ST_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					BRIGHOUSE_STATION, //
					HAMILTON_PARK, //
					QUEENSBORO, //
					RAILWAY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(RAILWAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 430l) {
			if (Arrays.asList( //
					KNIGHT, //
					BRIGHOUSE_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIGHOUSE_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 501l) {
			if (Arrays.asList( //
					CARVOLTH_EXCH, //
					LANGLEY_CTR //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LANGLEY_CTR, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 503l) {
			if (Arrays.asList( //
					LANGLEY_CTR, //
					SURREY_CENTRAL_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 509L) {
			if (Arrays.asList( //
					CARVOLTH_EXCH, //
					WALNUT_GROVE //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(WALNUT_GROVE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 531l) {
			if (Arrays.asList( //
					LANGLEY_CTR, //
					WILLOWBROOK //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(WILLOWBROOK, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 555l) {
			if (Arrays.asList( //
					PT_MANN_EXP, //
					CARVOLTH_EXCH //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(CARVOLTH_EXCH, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					PT_MANN_EXP, //
					LOUGHEED_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LOUGHEED_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 601l) {
			if (Arrays.asList( //
					STEVESTON_HWY, //
					BRIDGEPORT_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					BOUNDARY_BAY, //
					LADNER_EXCH, //
					TSAWW_HEIGTHS, //
					SOUTH_DELTA + SPACE + EXCHANGE_SHORT, //
					SOUTH_DELTA //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SOUTH_DELTA, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 603l) {
			if (Arrays.asList( //
					S_DELTA_EXCH, //
					BRIDGEPORT_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 604l) {
			if (Arrays.asList( //
					S_DELTA_EXCH, //
					BRIDGEPORT_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 640l) {
			if (Arrays.asList( //
					SPECIAL, //
					LADNER_EXCH //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LADNER_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 701l) {
			if (Arrays.asList( //
					HANEY_PLACE, //
					MISSION_CITY_STATION, //
					MAPLE_RIDGE_E, //
					MAPLE_RIDGE_EAST //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MAPLE_RIDGE_EAST, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 3l) { // C3
			if (Arrays.asList( //
					NEW_WEST_STATION, //
					VICTORIA_HILL //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VICTORIA_HILL, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					NEW_WEST_STATION, //
					QUAYSIDE //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(QUAYSIDE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 10l) { // C10
			if (Arrays.asList( //
					SPECIAL, //
					BLUEWATER //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BLUEWATER, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 12l) { // C12
			if (Arrays.asList( //
					HORSESHOE_BAY, //
					LIONS_BAY + "- " + BRUNSWICK, //
					LIONS_BAY //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LIONS_BAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 41l) { // C41
			if (Arrays.asList( //
					MAPLE_MEADOWS_STATION, //
					MEADOWTOWN, //
					MAPLE_MEADOWS_STATION + SLASH + MEADOWTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MAPLE_MEADOWS_STATION + SLASH + MEADOWTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 43l) { // C43
			if (Arrays.asList( //
					MAPLE_MEADOWS_STATION, //
					SOUTH_HANEY, //
					MEADOWTOWN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MEADOWTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 44l) { // C44
			if (Arrays.asList( //
					MAPLE_MEADOWS_STATION, //
					MEADOWTOWN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MEADOWTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 45l) { // C45
			if (Arrays.asList( //
					PT_HANEY_STATION, //
					HANEY_PLACE //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(HANEY_PLACE, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 46l) { // C46
			if (Arrays.asList( //
					PT_HANEY_STATION, //
					SPECIAL, //
					ALBION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(ALBION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 76l) { // C76
			if (Arrays.asList( //
					SCOTTSDALE, //
					SCOTTSDALE_EXCH //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SCOTTSDALE_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 93l) { // C93
			if (Arrays.asList( //
					SPECIAL, //
					RIVERPORT //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(RIVERPORT, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					SPECIAL, //
					STEVESTON //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(STEVESTON, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_C + 98l) { // C98
			if (Arrays.asList( //
					BOUNDARY_RD, //
					_22ND_ST_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					BOUNDARY_RD, //
					KINGSWOOD //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(KINGSWOOD, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_N + 9l) { // N9
			if (Arrays.asList( //
					LOUGHEED_STATION, //
					DOWNTOWN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_N + 10l) { // N10
			if (Arrays.asList( //
					_71, //
					DOWNTOWN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_N + 19l) { // N19
			if (Arrays.asList( //
					NEW_WEST_STATION, //
					SURREY_CENTRAL_STATION //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == RID_SW_N + 24l) { // N24
			if (Arrays.asList( //
					_3RD + AND + ST_DAVIDS, //
					DOWNTOWN //
					).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		}
		System.out.printf("\nUnexpected tripts to merge %s & %s!\n", mTrip, mTripToMerge);
		System.exit(-1);
		return false;
	}

	private static final Pattern TO = Pattern.compile("((^|\\W){1}(to)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final Pattern VIA = Pattern.compile("((^|\\W){1}(via)(\\W|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_QUOTE = Pattern.compile("(^\")", Pattern.CASE_INSENSITIVE);

	private static final Pattern ENDS_WITH_QUOTE = Pattern.compile("(\"[;]?[\\s]?$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_ROUTE = Pattern.compile("(^[A-Z]{0,1}[0-9]{1,3}[\\s]+{1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern EXPRESS = Pattern.compile("(\\-?( express|express )\\-?)", Pattern.CASE_INSENSITIVE);

	private static final Pattern SPECIAL_ = Pattern.compile("(\\-?( special|special )\\-?)", Pattern.CASE_INSENSITIVE);

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

	private static final Pattern SURREY_ = Pattern.compile("((^|\\s){1}(surrey)(\\s|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String SURREY_REPLACEMENT = "$2" + SURREY_SHORT + "$4";

	private static final Pattern ENDS_WITH_B_LINE = Pattern.compile("((^|\\s){1}(\\- )?(b\\-line)(\\s|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern U_B_C = Pattern.compile("((^|\\W){1}(ubc|u b c)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String U_B_C_REPLACEMENT = "$2" + UBC + "$4";

	private static final Pattern S_F_U = Pattern.compile("((^|\\W){1}(sfu|s f u)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String S_F_U_REPLACEMENT = "$2" + SFU + "$4";

	private static final Pattern V_C_C = Pattern.compile("((^|\\W){1}(vcc|v c c)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String V_C_C_REPLACEMENT = "$2" + VCC + "$4";

	private static final Pattern CENTRAL = Pattern.compile("((^|\\W){1}(central)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String CENTRAL_REPLACEMENT = "$2" + CENTRAL_SHORT + "$4";

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
		tripHeadsign = CENTRAL.matcher(tripHeadsign).replaceAll(CENTRAL_REPLACEMENT);
		tripHeadsign = EXCHANGE.matcher(tripHeadsign).replaceAll(EXCHANGE_REPLACEMENT);
		tripHeadsign = S_F_U.matcher(tripHeadsign).replaceAll(S_F_U_REPLACEMENT);
		tripHeadsign = U_B_C.matcher(tripHeadsign).replaceAll(U_B_C_REPLACEMENT);
		tripHeadsign = V_C_C.matcher(tripHeadsign).replaceAll(V_C_C_REPLACEMENT);
		tripHeadsign = UNIVERSITY.matcher(tripHeadsign).replaceAll(UNIVERSITY_REPLACEMENT);
		tripHeadsign = PORT_COQUITLAM.matcher(tripHeadsign).replaceAll(PORT_COQUITLAM_REPLACEMENT);
		tripHeadsign = COQUITLAM.matcher(tripHeadsign).replaceAll(COQUITLAM_REPLACEMENT);
		tripHeadsign = STATION.matcher(tripHeadsign).replaceAll(STATION_REPLACEMENT);
		tripHeadsign = SURREY_.matcher(tripHeadsign).replaceAll(SURREY_REPLACEMENT);
		tripHeadsign = PORT.matcher(tripHeadsign).replaceAll(PORT_REPLACEMENT);
		tripHeadsign = NIGHTBUS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = EXPRESS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = SPECIAL_.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
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
		gStopName = CENTRAL.matcher(gStopName).replaceAll(CENTRAL_REPLACEMENT);
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
