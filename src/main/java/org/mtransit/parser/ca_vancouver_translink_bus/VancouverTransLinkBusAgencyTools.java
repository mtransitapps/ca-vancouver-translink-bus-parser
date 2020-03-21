package org.mtransit.parser.ca_vancouver_translink_bus;

import org.apache.commons.lang3.StringUtils;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.MTLog;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// http://www.translink.ca/en/Schedules-and-Maps/Developer-Resources.aspx
// http://www.translink.ca/en/Schedules-and-Maps/Developer-Resources/GTFS-Data.aspx
// http://mapexport.translink.bc.ca/current/google_transit.zip
// http://ns.translink.ca/gtfs/notifications.zip
// http://ns.translink.ca/gtfs/google_transit.zip
// http://gtfs.translink.ca/static/latest
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
		MTLog.log("Generating TransLink bus data...");
		long start = System.currentTimeMillis();
		this.serviceIds = extractUsefulServiceIds(args, this, true);
		super.start(args);
		MTLog.log("Generating TransLink bus data... DONE in %s.", Utils.getPrettyDuration(System.currentTimeMillis() - start));
	}

	@Override
	public boolean excludingAll() {
		return this.serviceIds != null && this.serviceIds.isEmpty();
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

	private static final List<String> EXCLUDE_RSN = Arrays.asList(
			"980", // CANADA LINE SKYTRAIN
			"991", // MILLENNIUM SKYTRAIN
			"992", // EXPO SKYTRAIN
			"997", // WEST COAST EXPRESS
			"998" // SEABUS
	);

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
	private static final String R = "R";

	private static final long RID_SW_C = 30_000L;
	private static final long RID_SW_N = 140_000L;
	private static final long RID_SW_P = 160_000L;
	private static final long RID_SW_R = 180_000L;

	private HashMap<Long, Long> routeOriginalIdToRSN = new HashMap<>();

	@Override
	public long getRouteId(GRoute gRoute) {
		long rsn = -1L;
		if (Utils.isDigitsOnly(gRoute.getRouteShortName())) {
			rsn = Long.parseLong(gRoute.getRouteShortName()); // use route short name as route ID
		} else {
			Matcher matcher = DIGITS.matcher(gRoute.getRouteShortName());
			if (matcher.find()) {
				long id = Long.parseLong(matcher.group());
				if (gRoute.getRouteShortName().startsWith(C)) {
					rsn = RID_SW_C + id;
				} else if (gRoute.getRouteShortName().startsWith(N)) {
					rsn = RID_SW_N + id;
				} else if (gRoute.getRouteShortName().startsWith(P)) {
					rsn = RID_SW_P + id;
				} else if (gRoute.getRouteShortName().startsWith(R)) {
					rsn = RID_SW_R + id;
				}
			}
		}
		if (rsn == -1L) {
			MTLog.logFatal("Unexpected route ID %s", gRoute);
			return -1L;
		}
		this.routeOriginalIdToRSN.put(super.getRouteId(gRoute), rsn);
		// TODO export original route ID
		return super.getRouteId(gRoute); // useful to match with GTFS real-time
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
	private static final String RAPID_BUS_COLOR = "199354"; // GREEN (from PDF map)

	private static final String B_LINE = "B-LINE";

	@Override
	public String getRouteColor(GRoute gRoute) {
		if (gRoute.getRouteShortName().startsWith(N)) {
			return NIGHT_BUS_COLOR;
		}
		if (gRoute.getRouteShortName().startsWith(R)) {
			return RAPID_BUS_COLOR;
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
	private static final String BRIGHOUSE = "Brighouse";
	private static final String BRIGHOUSE_STATION = BRIGHOUSE + " " + STATION_SHORT;
	private static final String BRIDGEPORT = "Bridgeport";
	private static final String BRIDGEPORT_STATION = BRIDGEPORT + " " + STATION_SHORT;
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
	private static final String LYNN_VALLEY_CENTER = LYNN_VALLEY + SPACE + CENTER_SHORT;
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
	private static final String _16TH = "16th";
	private static final String KNIGHT_STREET = KNIGHT + SPACE + STREET_SHORT;
	private static final String VICTORIA = "Victoria";
	private static final String VICTORIA_HILL = VICTORIA + SPACE + HILL_SHORT;
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
	private static final String GUILDFORD_EXCH = GUILDFORD + " " + EXCHANGE_SHORT;
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
	private static final String MAIN_ST_STA = MAIN + SPACE + STREET_SHORT + SPACE + STATION_SHORT;
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
	private static final String QUAYSIDE = "Quayside";
	private static final String QUAYSIDE_DRIVE = QUAYSIDE + " Dr";
	private static final String MAPLE_MEADOWS_STATION = "Maple " + MEADOWS_SHORT + SPACE + STATION_SHORT;
	private static final String ST_DAVIDS = "St Davids";
	private static final String _3RD = "3rd";
	private static final String _71 = "71";
	private static final String KINGSWOOD = "Kingswood";
	private static final String KINGSWAY = "Kingsway";
	private static final String ALDERGROVE = "Aldergrove";

	private static HashMap<Long, RouteTripSpec> ALL_ROUTE_TRIPS2;

	static {
		HashMap<Long, RouteTripSpec> map2 = new HashMap<>();
		map2.put(38_311L, new RouteTripSpec(38_311L, // R2 // SPLITTED TO FIX SAME trip_headsign FOR 2 DIRECTIONS
				0, MTrip.HEADSIGN_TYPE_STRING, PHIBBS_EXCHANGE, //
				1, MTrip.HEADSIGN_TYPE_STRING, PARK_ROYAL) //
				.addTripSort(0, //
						Arrays.asList(
								Stops.getALL_STOPS().get("54411"), // EB Marine Dr @ Park Royal South
								Stops.getALL_STOPS().get("54100") // Phibbs Exchange @ Bay 2
						)) //
				.addTripSort(1, //
						Arrays.asList(
								Stops.getALL_STOPS().get("54100"), // Phibbs Exchange @ Bay 2
								Stops.getALL_STOPS().get("61769") // EB Marine Dr @ South Mall Access Layover
						)) //
				.compileBothTripSort());
		map2.put(20_667L, new RouteTripSpec(20_667L, // 555
				0, MTrip.HEADSIGN_TYPE_STRING, CARVOLTH_EXCH, //
				1, MTrip.HEADSIGN_TYPE_STRING, LOUGHEED_STATION) //
				.addTripSort(0, //
						Arrays.asList(//
								Stops.getALL_STOPS().get("58432"), // LOUGHEED STN BAY 8
								Stops.getALL_STOPS().get("61709") // CARVOLTH EXCHANGE BAY 1
						)) //
				.addTripSort(1, //
						Arrays.asList(//
								Stops.getALL_STOPS().get("61717"), // CARVOLTH EXCHANGE BAY 9
								Stops.getALL_STOPS().get("58432") // LOUGHEED STN BAY 6
						)) //
				.compileBothTripSort());
		map2.put(6_745L, new RouteTripSpec(6_745L, // 606
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("57499"), // NB 46A St @ River Rd W
								Stops.getALL_STOPS().get("57518") // Ladner Exchange @ Bay 7
						)) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("57518"), // Ladner Exchange @ Bay 7
								Stops.getALL_STOPS().get("57499") // NB 46A St @ River Rd W
						)) //
				.compileBothTripSort());
		map2.put(6_746L, new RouteTripSpec(6_746L, // 608
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("57529"), // SB 46A St @ River Rd West
								Stops.getALL_STOPS().get("57518") // Ladner Exch @ Bay 7
						)) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("57518"), // Ladner Exch @ Bay 7
								Stops.getALL_STOPS().get("57529") // SB 46A St @ River Rd West
						)) //
				.compileBothTripSort());
		map2.put(8_291L, new RouteTripSpec(8_291L, // 804
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("55688"), // Newton Exch @ Bay 7
								Stops.getALL_STOPS().get("58261") // WB 88 Ave @ 160 St
						)) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("55862"), // SB 160 St @ 88 Ave
								Stops.getALL_STOPS().get("54793") // Scottsdale Exch @ Bay 6
						)) //
				.compileBothTripSort());
		map2.put(6_754L, new RouteTripSpec(6_754L, // 855
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("56365"), // WB 24 Ave @ 134 St
								Stops.getALL_STOPS().get("58600") // WB 32 Ave @ 152 St
						)) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("56075"), // SB 152 St @ 32 Ave
								Stops.getALL_STOPS().get("56372") // EB 24 Ave @ 136 St
						)) //
				.compileBothTripSort());
		map2.put(6_755L, new RouteTripSpec(6_755L, // 861
				MDirectionType.NORTH.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.NORTH.getId(), //
				MDirectionType.SOUTH.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.SOUTH.getId()) //
				.addTripSort(MDirectionType.NORTH.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("53771"), // NB Western Dr @ Eastern Dr
								Stops.getALL_STOPS().get("53025") // SB Shaughnessy St @ McAllister Ave
						)) //
				.addTripSort(MDirectionType.SOUTH.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("53737"), // NB Reeve St @ Hawthorne Ave
								Stops.getALL_STOPS().get("53771") // NB Western Dr @ Eastern Dr
						)) //
				.compileBothTripSort());
		map2.put(6_761L, new RouteTripSpec(6_761L, // 880
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("54423"), // Capilano University @ Bay 5
								Stops.getALL_STOPS().get("54101") // NB Emerson Way @ Mt Seymour Parkway
						)) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("54792"), // WB Mt Seymour Parkway @ Lytton St
								Stops.getALL_STOPS().get("54423") // Capilano University @ Bay 5
						)) //
				.compileBothTripSort());
		map2.put(6_762L, new RouteTripSpec(6_762L, // 881
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.EAST.getId(), //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_DIRECTION, MDirectionType.WEST.getId()) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("54390"), // EB W 22 St @ Philip Ave
								Stops.getALL_STOPS().get("61336"), // != EB Larson Rd @ Fir St
								Stops.getALL_STOPS().get("57943"), // <> SB Jones Ave @ W 21 St
								Stops.getALL_STOPS().get("54454"), // != // EB W 15 St @ Jones Ave
								Stops.getALL_STOPS().get("54179") // EB E 15 St @ Grand Blvd East
						)) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(
								Stops.getALL_STOPS().get("57943"), // <> SB Jones Ave @ W 21 St
								Stops.getALL_STOPS().get("54467"),// != SB Jones Ave @ W 15 St
								Stops.getALL_STOPS().get("54389") // SB Jones Ave @ W 21 St
						)) //
				.compileBothTripSort());
		ALL_ROUTE_TRIPS2 = map2;
	}

	@Override
	public int compareEarly(long routeId, List<MTripStop> list1, List<MTripStop> list2, MTripStop ts1, MTripStop ts2, GStop ts1GStop, GStop ts2GStop) {
		if (ALL_ROUTE_TRIPS2.containsKey(routeId)) {
			return ALL_ROUTE_TRIPS2.get(routeId).compare(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop, this);
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
			return SplitUtils.splitTripStop(mRoute, gTrip, gTripStop, routeGTFS, ALL_ROUTE_TRIPS2.get(mRoute.getId()), this);
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
		List<String> headsignsValues = Arrays.asList(mTrip.getHeadsignValue(), mTripToMerge.getHeadsignValue());
		if (headsignsValues.contains(SPECIAL)) {
			if (SPECIAL.equals(mTrip.getHeadsignValue())) {
				mTrip.setHeadsignString(mTripToMerge.getHeadsignValue(), mTrip.getHeadsignId());
				return true;
			} else if (SPECIAL.equals(mTripToMerge.getHeadsignValue())) {
				mTripToMerge.setHeadsignString(mTrip.getHeadsignValue(), mTripToMerge.getHeadsignId());
				return true;
			}
		}
		long rsn = this.routeOriginalIdToRSN.get(mTrip.getRouteId());
		if (rsn == 2L) {
			if (Arrays.asList( //
					BURRARD_STATION, //
					DOWNTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					MACDONALD + DASH + _16_AVE, //
					MACDONALD + DASH + _16TH, //
					MACDONALD //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MACDONALD, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 3L) {
			if (Arrays.asList( //
					MAIN_MARINE_DR_STATION, //
					MAIN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MAIN_MARINE_DR_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 4L) {
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
		} else if (rsn == 7L) {
			if (Arrays.asList( //
					DOWNTOWN, //
					HASTINGS, //
					DUNBAR //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DUNBAR, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 8L) {
			if (Arrays.asList( //
					_41ST, //
					DOWNTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 9L) {
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
					OAK, //
					UBC //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(ALMA, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 10L) {
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
		} else if (rsn == 14L) {
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
		} else if (rsn == 16L) {
			if (Arrays.asList( //
					DOWNTOWN, //
					KINGSWAY, //
					_29TH_AVE_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_29TH_AVE_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					"Pne", //
					HASTINGS, //
					ARBUTUS //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(ARBUTUS, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 17L) {
			if (Arrays.asList( //
					BROADWAY, //
					DOWNTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 19L) {
			if (Arrays.asList( //
					DOWNTOWN, //
					GRANVILLE, //
					MAIN_ST_STA, //
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
		} else if (rsn == 20L) {
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
		} else if (rsn == 22L) {
			if (Arrays.asList( //
					_63RD, //
					KNIGHT //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(KNIGHT, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 25L) {
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
		} else if (rsn == 27L) {
			if (Arrays.asList( //
					JOYCE_STATION, //
					KOOTENAY_LOOP //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(KOOTENAY_LOOP, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 28L) {
			if (Arrays.asList( //
					KOOTENAY_LOOP, // <>
					PHIBBS_EXCHANGE // <>
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(PHIBBS_EXCHANGE, mTrip.getHeadsignId()); // SUMMER
				return true;
			} else if (Arrays.asList( //
					KOOTENAY_LOOP, // <>
					PHIBBS_EXCHANGE, // <>
					CAPILANO_U //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(CAPILANO_U, mTrip.getHeadsignId()); // WINTER
				return true;
			}
			if (Arrays.asList( //
					KOOTENAY_LOOP, // <>
					PHIBBS_EXCHANGE, // <>
					GILMORRE_STATION, //
					JOYCE_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(JOYCE_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 41L) {
			if (Arrays.asList( //
					GRANVILLE, //
					_41ST_OAK, //
					JOYCE_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(JOYCE_STATION, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					GRANVILLE, //
					_41ST_OAK, //
					CROWN, //
					DUNBAR, //
					UBC //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 49L) {
			if (Arrays.asList( //
					"Langara-49th Ave Sta", //
					"Langara-49th Sta", //
					GRANVILLE, //
					DUNBAR_LOOP, //
					UBC //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UBC, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 95L) {
			if (Arrays.asList( //
					KOOTENAY_LOOP, //
					WILLINGDON, //
					SFU //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SFU, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					"Waterfront Sta", //
					BURRARD_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BURRARD_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 96L) {
			if (Arrays.asList( //
					GUILDFORD_EXCH, //
					GUILDFORD //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(GUILDFORD, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 99L) {
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
		} else if (rsn == 100L) {
			if (Arrays.asList( //
					KNIGHT_STREET, //
					_22ND_ST_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 103L) {
			if (Arrays.asList( //
					NEW_WEST_STATION, // <>
					QUAYSIDE //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(QUAYSIDE, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					NEW_WEST_STATION, // <>
					VICTORIA_HILL //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VICTORIA_HILL, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 110L) {
			if (Arrays.asList( //
					CITY_HALL, //
					LOUGHEED_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LOUGHEED_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					SPERLING_STATION, //
					METROTOWN_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(METROTOWN_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 112L) {
			if (Arrays.asList( //
					METROTOWN_STATION, //
					EDMONDS_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(EDMONDS_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 130L) {
			if (Arrays.asList( //
					CAPILANO_U, //
					HASTINGS, //
					KOOTENAY_LOOP, //
					"Pender", //
					PHIBBS_EXCHANGE //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(PHIBBS_EXCHANGE, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 143L) {
			if (Arrays.asList( //
					BURQUITLAM_STATION, // <>
					SFU //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SFU, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 159L) {
			if (Arrays.asList( //
					"Brad Sta", //
					BRAID_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRAID_STATION, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					COQUITLAM_SHORT + " " + CENTER_SHORT + " " + STATION_SHORT, //
					COQUITLAM_CENTRAL_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(COQUITLAM_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 181L) {
			if (Arrays.asList( //
					IOCO, //
					BELCARRA //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BELCARRA, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 186L) {
			if (Arrays.asList( //
					"Panorama", //
					"Hampton Pk" //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Hampton Pk", mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 187L) {
			if (Arrays.asList( //
					"Panorama", //
					"Pkwy" //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Pkwy", mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 209L) {
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					VANCOUVER //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					BURRARD_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BURRARD_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 210L) {
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					UPPER_LYNN_VALLEY //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UPPER_LYNN_VALLEY, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					"Pne", //
					PHIBBS_EXCHANGE, //
					VANCOUVER //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					BURRARD_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BURRARD_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 211L) {
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					VANCOUVER //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					BURRARD_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BURRARD_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 214L) {
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					VANCOUVER //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					PHIBBS_EXCHANGE, //
					BURRARD_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BURRARD_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 227L) {
			if (Arrays.asList( //
					LYNN_VALLEY_CENTER, // <>
					PHIBBS_EXCHANGE //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(PHIBBS_EXCHANGE, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 228L) {
			if (Arrays.asList( //
					GRAND_BLVD + AND + _15_ST, //
					LONSDALE_QUAY //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LONSDALE_QUAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 230L) {
			if (Arrays.asList( //
					STREET_SHORT, //
					UPPER_LONSDALE //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(UPPER_LONSDALE, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 232L) {
			if (Arrays.asList( //
					EDGEMONT_VILLAGE, //
					GROUSE_MTN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(GROUSE_MTN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 236L) {
			if (Arrays.asList( //
					PEMBERTON_HEIGHTS, //
					GROUSE_MTN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(GROUSE_MTN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 239L) {
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
		} else if (rsn == 240L) {
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
			if (Arrays.asList( //
					_15TH_ST, //
					DOWNTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 242L) {
			if (Arrays.asList( //
					LONSDALE_QUAY, //
					LYNN_VALLEY //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LYNN_VALLEY, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 246L) {
			if (Arrays.asList( //
					HIGHLAND, //
					LONSDALE_QUAY //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LONSDALE_QUAY, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					HIGHLAND, //
					MARINE + AND + GARDEN_SHORT, //
					MARINE + AND + CAPILANO, //
					VANCOUVER //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					MARINE + AND + GARDEN_SHORT, //
					MARINE + AND + CAPILANO, //
					DOWNTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 247L) {
			if (Arrays.asList( //
					UPPER_CAPILANO, //
					GROUSE_MTN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(GROUSE_MTN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 250L) {
			if (Arrays.asList( //
					PARK_ROYAL, //
					"Vancouver Events Bus", // TODO cleanup ?
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
		} else if (rsn == 252L) {
			if (Arrays.asList( //
					WEST_VAN_SECONDARY, //
					INGLEWOOD //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(INGLEWOOD, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 253L) {
			if (Arrays.asList( //
					PARK_ROYAL, //
					VANCOUVER //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 254L) {
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
		} else if (rsn == 255L) {
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
		} else if (rsn == 256L) {
			if (Arrays.asList( //
					PARK_ROYAL, //
					SPURAWAY //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SPURAWAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 257L) {
			if (Arrays.asList( //
					PARK_ROYAL, //
					VANCOUVER //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VANCOUVER, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					PARK_ROYAL, //
					"Express-" + HORSESHOE_BAY, //
					HORSESHOE_BAY + AND + LIONS_BAY, //
					HORSESHOE_BAY //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(HORSESHOE_BAY, mTrip.getHeadsignId()); //
				return true;
			}
		} else if (rsn == 259L) {
			if (Arrays.asList( //
					HORSESHOE_BAY, //
					PARK_ROYAL //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(PARK_ROYAL, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 262L) {
			if (Arrays.asList( //
					HORSESHOE_BAY, //
					LIONS_BAY + "- " + BRUNSWICK //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LIONS_BAY + "- " + BRUNSWICK, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 281L) {
			if (Arrays.asList( //
					"Snug Cv", //
					"Eaglecliff" //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Eaglecliff", mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 314L) {
			if (Arrays.asList( //
					SCOTT + AND + _96_AVE, //
					SUNBURY //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SUNBURY, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 319L) {
			if (Arrays.asList( //
					SCOTTSDALE, //
					NEWTON_EXCH //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(NEWTON_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 320L) {
			if (Arrays.asList( //
					FLEETWOOD, //
					LANGLEY_CTR //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LANGLEY_CTR, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 321L) {
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
		} else if (rsn == 323L) {
			if (Arrays.asList( //
					_76TH + AND + _132ND, //
					NEWTON_EXCH //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(NEWTON_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 329L) {
			if (Arrays.asList( //
					SCOTT + AND + _96_AVE, //
					SURREY_CENTRAL_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 335L) {
			if (Arrays.asList( //
					GUILDFORD, //
					NEWTON_EXCH //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(NEWTON_EXCH, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					GUILDFORD, //
					SURREY_CENTRAL_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 337L) {
			if (Arrays.asList( //
					GUILDFORD, //
					SURREY_CENTRAL_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 351L) {
			if (Arrays.asList( //
					WHITE_ROCK + SPACE + CENTER_SHORT, //
					"S Sry " + "Pk & Ride", //
					CRESCENT_BEACH //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(CRESCENT_BEACH, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 373L) {
			if (Arrays.asList( //
					"Kindersley Dr", //
					GUILDFORD //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(GUILDFORD, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 401L) {
			if (Arrays.asList( //
					HORSESHOE_WAY, //
					RIVERPORT //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(RIVERPORT, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 403L) {
			if (Arrays.asList( //
					FIVE_RD, //
					BRIDGEPORT_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 404L) {
			if (Arrays.asList( //
					STEVESTON + AND + _5_RD, //
					BRIGHOUSE_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIGHOUSE_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 405L) {
			if (Arrays.asList( //
					BRIGHOUSE_STATION, //
					FIVE_RD //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(FIVE_RD, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 407L) {
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
		} else if (rsn == 408L) {
			if (Arrays.asList( //
					"Ironwood", //
					RIVERPORT //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(RIVERPORT, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 410L) {
			if (Arrays.asList( //
					StringUtils.EMPTY, // <>
					BRIGHOUSE_STATION, // <>
					BOUNDARY_RD, //
					_22ND_ST_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					StringUtils.EMPTY, // <>
					BRIGHOUSE_STATION, // <>
					BRIGHOUSE, //
					HAMILTON_PARK, //
					QUEENSBORO, //
					RAILWAY //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(RAILWAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 418L) {
			if (Arrays.asList( //
					BOUNDARY_RD, // <>
					_22ND_ST_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(_22ND_ST_STATION, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					BOUNDARY_RD, // <>
					KINGSWOOD //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(KINGSWOOD, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 430L) {
			if (Arrays.asList( //
					KNIGHT, //
					BRIGHOUSE_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIGHOUSE_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 501L) {
			if (Arrays.asList( //
					CARVOLTH_EXCH, //
					LANGLEY_CTR //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LANGLEY_CTR, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 503L) {
			if (Arrays.asList( //
					LANGLEY_CTR, //
					SURREY_CENTRAL_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					LANGLEY_CTR, //
					ALDERGROVE //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(ALDERGROVE, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 509L) {
			if (Arrays.asList( //
					WALNUT_GROVE, // <>
					SURREY_CENTRAL_STATION // <>
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					LANGLEY_CTR, //
					CARVOLTH_EXCH, //
					SURREY_CENTRAL_STATION, // <>
					WALNUT_GROVE // <>
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(WALNUT_GROVE, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 531L) {
			if (Arrays.asList( //
					LANGLEY_CTR, //
					WILLOWBROOK //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(WILLOWBROOK, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 555L) {
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
		} else if (rsn == 595L) {
			if (Arrays.asList( //
					MAPLE_MEADOWS_STATION, //
					"Rdg Mdws" //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Rdg Mdws", mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 601L) {
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
		} else if (rsn == 603L) {
			if (Arrays.asList( //
					S_DELTA_EXCH, //
					BRIDGEPORT_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 604L) {
			if (Arrays.asList( //
					S_DELTA_EXCH, //
					BRIDGEPORT_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BRIDGEPORT_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 616L) {
			if (Arrays.asList( //
					LADNER + " South", //
					"South " + LADNER //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("South " + LADNER, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 618L) {
			if (Arrays.asList( //
					LADNER + " North", //
					"North " + LADNER //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("North " + LADNER, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 640L) {
			if (Arrays.asList( //
					"Tillbury", //
					LADNER_EXCH //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LADNER_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 701L) {
			if (Arrays.asList( //
					HANEY_PLACE, // ==
					MAPLE_RIDGE_E, //
					MAPLE_RIDGE_EAST, //
					MISSION_CITY_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MISSION_CITY_STATION, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					HANEY_PLACE, // ==
					COQUITLAM_CENTRAL_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(COQUITLAM_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 733L) {
			if (Arrays.asList( //
					HANEY_PLACE, //
					PT_HANEY_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(PT_HANEY_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 741L) {
			if (Arrays.asList( //
					HANEY_PLACE, //
					PT_HANEY_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(PT_HANEY_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 743L) {
			if (Arrays.asList( //
					SOUTH_HANEY, //
					MAPLE_MEADOWS_STATION, //
					MEADOWTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MEADOWTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 744L) {
			if (Arrays.asList( //
					MAPLE_MEADOWS_STATION, //
					MEADOWTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MEADOWTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == 746L) {
			if (Arrays.asList( //
					PT_HANEY_STATION, // ==
					HANEY_PLACE //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(HANEY_PLACE, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					PT_HANEY_STATION, // ==
					ALBION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(ALBION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_C + 3L) { // C3
			if (Arrays.asList( //
					NEW_WEST_STATION, //
					VICTORIA_HILL //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(VICTORIA_HILL, mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					NEW_WEST_STATION, //
					QUAYSIDE_DRIVE, //
					QUAYSIDE //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(QUAYSIDE, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_C + 12L) { // C12
			if (Arrays.asList( //
					HORSESHOE_BAY, //
					LIONS_BAY + "- " + BRUNSWICK, //
					LIONS_BAY //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LIONS_BAY, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_C + 41L) { // C41
			if (Arrays.asList( //
					MAPLE_MEADOWS_STATION, //
					MEADOWTOWN, //
					MAPLE_MEADOWS_STATION + SLASH + MEADOWTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MAPLE_MEADOWS_STATION + SLASH + MEADOWTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_C + 43L) { // C43
			if (Arrays.asList( //
					MAPLE_MEADOWS_STATION, //
					SOUTH_HANEY, //
					MEADOWTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MEADOWTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_C + 44L) { // C44
			if (Arrays.asList( //
					MAPLE_MEADOWS_STATION, //
					MEADOWTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(MEADOWTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_C + 45L) { // C45
			if (Arrays.asList( //
					PT_HANEY_STATION, //
					HANEY_PLACE //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(HANEY_PLACE, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_C + 46L) { // C46
			if (Arrays.asList( //
					PT_HANEY_STATION, //
					ALBION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(ALBION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_C + 76L) { // C76
			if (Arrays.asList( //
					SCOTTSDALE, //
					SCOTTSDALE_EXCH //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SCOTTSDALE_EXCH, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_C + 98L) { // C98
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
		} else if (rsn == RID_SW_N + 9L) { // N9
			if (Arrays.asList( //
					"Production Sta", //
					LOUGHEED_STATION, //
					DOWNTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_N + 10L) { // N10
			if (Arrays.asList( //
					_71, //
					DOWNTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_N + 19L) { // N19
			if (Arrays.asList( //
					NEW_WEST_STATION, //
					SURREY_CENTRAL_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STATION, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_N + 24L) { // N24
			if (Arrays.asList( //
					LONSDALE_QUAY, //
					_3RD + AND + ST_DAVIDS, //
					DOWNTOWN //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(DOWNTOWN, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					LONSDALE_QUAY, //
					LYNN_VALLEY //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(LYNN_VALLEY, mTrip.getHeadsignId());
				return true;
			}
		} else if (rsn == RID_SW_R + 5L) { // R5
			if (Arrays.asList( //
					KOOTENAY_LOOP, //
					SFU //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(SFU, mTrip.getHeadsignId());
				return true;
			}
			if (Arrays.asList( //
					"Waterfront Sta", //
					BURRARD_STATION //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString(BURRARD_STATION, mTrip.getHeadsignId());
				return true;
			}
		}
		MTLog.logFatal("Unexpected trips to merge %s & %s!", mTrip, mTripToMerge);
		return false;
	}

	private static final Pattern TO = Pattern.compile("((^|\\W)(to)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final Pattern VIA = Pattern.compile("((^|\\W)(via)(\\W|$))", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_QUOTE = Pattern.compile("(^\")", Pattern.CASE_INSENSITIVE);

	private static final Pattern ENDS_WITH_QUOTE = Pattern.compile("(\"[;]?[\\s]?$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_ROUTE = Pattern.compile("(^[A-Z]?[0-9]{1,3}[\\s]+)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_SLASH = Pattern.compile("(^.* / )", Pattern.CASE_INSENSITIVE);

	private static final Pattern EXPRESS = Pattern.compile("(-?( express|express )-?)", Pattern.CASE_INSENSITIVE);

	private static final Pattern SPECIAL_ = Pattern.compile("(-?( special|special )-?)", Pattern.CASE_INSENSITIVE);

	private static final Pattern ONLY = Pattern.compile("(-?( only|only )-?)", Pattern.CASE_INSENSITIVE);

	private static final Pattern UNIVERSITY = Pattern.compile("((^|\\W)(university)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String UNIVERSITY_REPLACEMENT = "$2" + UNIVERSITY_SHORT + "$4";

	private static final Pattern PORT_COQUITLAM = Pattern.compile("((^|\\W)(port coquitlam|pt coquitlam|poco)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String PORT_COQUITLAM_REPLACEMENT = "$2" + PORT_COQUITLAM_SHORT + "$4";

	private static final Pattern COQUITLAM = Pattern.compile("((^|\\W)(coquitlam|coq)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String COQUITLAM_REPLACEMENT = "$2" + COQUITLAM_SHORT + "$4";

	private static final Pattern STATION = Pattern.compile("((^|\\W)(stn|sta|station)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String STATION_REPLACEMENT = "$2" + STATION_SHORT + "$4";

	private static final Pattern PORT = Pattern.compile("((^|\\W)(port)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String PORT_REPLACEMENT = "$2" + PORT_SHORT + "$4";

	private static final Pattern NIGHTBUS = Pattern.compile("((^|\\s)(nightbus)(\\s|$))", Pattern.CASE_INSENSITIVE);

	private static final Pattern EXCHANGE = Pattern.compile("((^|\\s)(exchange|exch)(\\s|$))", Pattern.CASE_INSENSITIVE);
	private static final String EXCHANGE_REPLACEMENT = "$2" + EXCHANGE_SHORT + "$4";

	private static final Pattern SURREY_ = Pattern.compile("((^|\\s)(surrey)(\\s|$))", Pattern.CASE_INSENSITIVE);
	private static final String SURREY_REPLACEMENT = "$2" + SURREY_SHORT + "$4";

	private static final Pattern ENDS_WITH_B_LINE = Pattern.compile("((^|\\s)(- )?(b-line)(\\s|$))", Pattern.CASE_INSENSITIVE);

	private static final Pattern U_B_C = Pattern.compile("((^|\\W)(ubc|u b c)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String U_B_C_REPLACEMENT = "$2" + UBC + "$4";

	private static final Pattern S_F_U = Pattern.compile("((^|\\W)(sfu|s f u)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String S_F_U_REPLACEMENT = "$2" + SFU + "$4";

	private static final Pattern V_C_C = Pattern.compile("((^|\\W)(vcc|v c c)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String V_C_C_REPLACEMENT = "$2" + VCC + "$4";

	private static final Pattern CENTRAL = Pattern.compile("((^|\\W)(central)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String CENTRAL_REPLACEMENT = "$2" + CENTRAL_SHORT + "$4";

	private static final Pattern BRAID_STATION_ = Pattern.compile("((^|\\W)(brad stn)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String BRAID_STATION_REPLACEMENT = "$2" + BRAID_STATION + "$4";

	private static final Pattern REMOVE_DASH = Pattern.compile("(^(\\s)*-(\\s)*|(\\s)*-(\\s)*$)", Pattern.CASE_INSENSITIVE);

	@Override
	public String cleanTripHeadsign(String tripHeadsign) {
		tripHeadsign = tripHeadsign.toLowerCase(Locale.ENGLISH);
		tripHeadsign = CleanUtils.cleanSlashes(tripHeadsign);
		tripHeadsign = STARTS_WITH_QUOTE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = ENDS_WITH_QUOTE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = STARTS_WITH_ROUTE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = STARTS_WITH_SLASH.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = ENDS_WITH_B_LINE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = CleanUtils.CLEAN_AND.matcher(tripHeadsign).replaceAll(CleanUtils.CLEAN_AND_REPLACEMENT);
		tripHeadsign = CleanUtils.CLEAN_AT.matcher(tripHeadsign).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		tripHeadsign = AT_LIKE.matcher(tripHeadsign).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		tripHeadsign = CENTRAL.matcher(tripHeadsign).replaceAll(CENTRAL_REPLACEMENT);
		tripHeadsign = EXCHANGE.matcher(tripHeadsign).replaceAll(EXCHANGE_REPLACEMENT);
		tripHeadsign = S_F_U.matcher(tripHeadsign).replaceAll(S_F_U_REPLACEMENT);
		tripHeadsign = U_B_C.matcher(tripHeadsign).replaceAll(U_B_C_REPLACEMENT);
		tripHeadsign = V_C_C.matcher(tripHeadsign).replaceAll(V_C_C_REPLACEMENT);
		tripHeadsign = BRAID_STATION_.matcher(tripHeadsign).replaceAll(BRAID_STATION_REPLACEMENT);
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

	private static final Pattern AT_LIKE = Pattern.compile("((^|\\W)(fs|ns)(\\W|$))", Pattern.CASE_INSENSITIVE);

	private static final Pattern FLAG_STOP = Pattern.compile("((^flagstop)[\\s]*(.*$))", Pattern.CASE_INSENSITIVE);
	private static final String FLAG_STOP_REPLACEMENT = "$3 ($2)";

	private static final Pattern EASTBOUND_ = Pattern.compile("((^|\\W)(eastbound)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String EASTBOUND_REPLACEMENT = "$2" + "EB" + "$4";

	private static final Pattern WESTBOUND_ = Pattern.compile("((^|\\W)(westbound)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String WESTBOUND_REPLACEMENT = "$2" + "WB" + "$4";

	private static final Pattern NORTHBOUND_ = Pattern.compile("((^|\\W)(northbound)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String NORTHBOUND_REPLACEMENT = "$2" + "NB" + "$4";

	private static final Pattern SOUTHBOUND_ = Pattern.compile("((^|\\W)(southbound)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String SOUTHBOUND_REPLACEMENT = "$2" + "SB" + "$4";

	private static final Pattern UNLOADING = Pattern.compile("(unloading( only)?$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern ENDS_WITH_DASHES = Pattern.compile("([\\-]+$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_AT = Pattern.compile("(^@ )", Pattern.CASE_INSENSITIVE);

	@Override
	public String cleanStopName(String gStopName) {
		if (Utils.isUppercaseOnly(gStopName, true, true)) {
			gStopName = gStopName.toLowerCase(Locale.ENGLISH);
		}
		gStopName = CleanUtils.cleanSlashes(gStopName);
		gStopName = EASTBOUND_.matcher(gStopName).replaceAll(EASTBOUND_REPLACEMENT);
		gStopName = WESTBOUND_.matcher(gStopName).replaceAll(WESTBOUND_REPLACEMENT);
		gStopName = NORTHBOUND_.matcher(gStopName).replaceAll(NORTHBOUND_REPLACEMENT);
		gStopName = SOUTHBOUND_.matcher(gStopName).replaceAll(SOUTHBOUND_REPLACEMENT);
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
		return super.getStopId(gStop); // using stop ID as stop code (useful to match with GTFS real-time)
	}
}
