package org.mtransit.parser.ca_vancouver_translink_bus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.Utils;
import org.mtransit.parser.gtfs.data.GCalendar;
import org.mtransit.parser.gtfs.data.GCalendarDate;
import org.mtransit.parser.gtfs.data.GRoute;
import org.mtransit.parser.gtfs.data.GSpec;
import org.mtransit.parser.gtfs.data.GStop;
import org.mtransit.parser.gtfs.data.GTrip;
import org.mtransit.parser.mt.data.MAgency;
import org.mtransit.parser.mt.data.MDirectionType;
import org.mtransit.parser.mt.data.MRoute;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.mt.data.MTrip;

// http://www.translink.ca/en/Schedules-and-Maps/Developer-Resources.aspx
// http://www.translink.ca/en/Schedules-and-Maps/Developer-Resources/GTFS-Data.aspx
// http://mapexport.translink.bc.ca/current/google_transit.zip
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
		if (EXCLUDE_RSN.contains(gRoute.route_short_name)) {
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
		if (Utils.isDigitsOnly(gRoute.route_short_name)) {
			return Long.parseLong(gRoute.route_short_name); // use route short name as route ID
		}
		Matcher matcher = DIGITS.matcher(gRoute.route_short_name);
		matcher.find();
		long id = Long.parseLong(matcher.group());
		if (gRoute.route_short_name.startsWith(C)) {
			return RID_SW_C + id;
		} else if (gRoute.route_short_name.startsWith(N)) {
			return RID_SW_N + id;
		} else if (gRoute.route_short_name.startsWith(P)) {
			return RID_SW_P + id;
		}
		System.out.printf("\nUnexpected route ID %s\n", gRoute);
		System.exit(-1);
		return -1l;
	}

	@Override
	public String getRouteShortName(GRoute gRoute) {
		String routeShortName = gRoute.route_short_name; // used by real-time API
		if (Utils.isDigitsOnly(routeShortName)) { // used by real-time API
			routeShortName = String.valueOf(Integer.valueOf(routeShortName)); // used by real-time API
		} // used by real-time API
		return routeShortName; // used by real-time API
	}

	@Override
	public String getRouteLongName(GRoute gRoute) {
		String gRouteLongName = gRoute.route_long_name;
		gRouteLongName = gRouteLongName.toLowerCase(Locale.ENGLISH);
		gRouteLongName = CleanUtils.CLEAN_SLASHES.matcher(gRouteLongName).replaceAll(CleanUtils.CLEAN_SLASHES_REPLACEMENT);
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
		if (gRoute.route_short_name.startsWith(N)) {
			return NIGHT_BUS_COLOR;
		}
		if (gRoute.route_long_name.contains(B_LINE)) {
			return B_LINE_BUS_COLOR;
		}
		return null; // use agency color
	}

	private static final String SLASH = " / ";

	private static final String ALBION = "Albion";
	private static final String HANEY_PL = "Haney Pl";
	private static final String MAPLE_MDWS_STN = "Maple Mdws Stn";
	private static final String MEADOWTOWN = "Meadowtown";
	private static final String POCO_STN = "Poco Stn";
	private static final String POCO_SOUTH = "Poco South";
	private static final String BELCARRA = "Belcarra";
	private static final String PT_MOODY_STN = "Pt Moody Stn";
	private static final String IOCO = "Ioco";
	private static final String LIONS_BAY = "Lions Bay";
	private static final String MAPLE_RDG_EAST = "Maple Rdg East";
	private static final String LADNER_EXCH = "Ladner Exch";
	private static final String CARVOLTH_EXCH = "Carvolth Exch";
	private static final String LOUGHEED_STN = "Lougheed Stn";
	private static final String GILBERT = "Gilbert";
	private static final String FIVE_RD = "Five Rd";
	private static final String BRIGHOUSE_STN = "Brighouse Stn";
	private static final String FOUR_RD = "Four Rd";
	private static final String BRIDGEPORT_STN = "Bridgeport Stn";
	private static final String CR_BEACH = "Cr Beach";
	private static final String WHITBY_ESTS = "Whitby Ests";
	private static final String WHITE_ROCK = "White Rock";
	private static final String NEWTON_EXCH = "Newton Exch";
	private static final String NEWTON_EXCH_WHITE_ROCK = NEWTON_EXCH + SLASH + WHITE_ROCK;
	private static final String SURREY_CENTRAL_STN = "Surrey Ctrl Stn";
	private static final String LANGLEY_CTR = "Langley Ctr";
	private static final String SUNBURY = "Sunbury";
	private static final String SPURAWAY = "Spuraway";
	private static final String DUNDARAVE = "Dundarave";
	private static final String BRITISH_PROPERTIES = "British Properties";
	private static final String UPPER_CAPILANO = "Upper Capilano";
	private static final String GROUSE_MTN = "Grouse Mtn";
	private static final String UPPER_LONSDALE = "Upper Lonsdale";
	private static final String LONSDALE_QUAY = "Lonsdale Quay";
	private static final String UPPER_LYNN_VLY = "Upper Lynn Vly";
	private static final String PORT_COQUITLAM_STN = "Port Coquitlam Stn";
	private static final String VANCOUVER = "Vancouver";
	private static final String HORSESHOE_BAY = "Horseshoe Bay";
	private static final String CAPILANO_U = "Capilano U";
	private static final String PK_ROYAL = "Pk Royal";
	private static final String SPURAWAY_PK_ROYAL = SPURAWAY + SLASH + PK_ROYAL;
	private static final String VANCOUVER_PK_ROYAL = VANCOUVER + SLASH + PK_ROYAL;
	private static final String GDN_CITY = "Gdn City";
	private static final String ONE_RD = "One Rd";
	private static final String RAILWAY = "Railway";
	private static final String _22ND_ST_STN = "22nd St Stn";
	private static final String DOWNTOWN = "Downtown";
	private static final String HASTINGS = "Hastings";
	private static final String HASTINGS_DOWNTOWN = HASTINGS + SLASH + DOWNTOWN;
	private static final String POWELL = "Powell";
	private static final String UBC = "UBC";
	private static final String CROWN = "Crown";
	private static final String PHIBBS_EXCH = "Phibbs Exch";
	private static final String JOYCE_STN = "Joyce Stn";
	private static final String KOOTENAY_LOOP = "Kootenay Loop";
	private static final String BRENTWOOD_STN = "Brentwood Stn";
	private static final String MACDONALD = "Macdonald";
	private static final String KNIGHT = "Knight";
	private static final String VICTORIA = "Victoria";
	private static final String METROTOWN_STN = "Metrotown Stn";
	private static final String STANLEY_PK = "Stanley Pk";
	private static final String CROWN_UBC = CROWN + SLASH + UBC;
	private static final String COMM_L_BDWAY_STN = "Comm''l-Bdway Stn";
	private static final String ALMA = "Alma";
	private static final String BOUNDARY = "Boundary";
	private static final String COMM_L_BDWAY_STN_BOUNDARY = COMM_L_BDWAY_STN + SLASH + BOUNDARY;
	private static final String GRANVILLE = "Granville";
	private static final String GRANVILLE_ALMA_UBC = GRANVILLE + SLASH + ALMA + SLASH + UBC;
	private static final String DUNBAR = "Dunbar";
	private static final String ARBUTUS = "Arbutus";
	private static final String SFU = "SFU";
	private static final String EDMONDS_STN = "Edmonds Stn";
	private static final String ANNACIS_ISL = "Annacis Isl";
	private static final String MAPLE_MDWS_STN_MEADOWTOWN = MAPLE_MDWS_STN + SLASH + MEADOWTOWN;

	@Override
	public void setTripHeadsign(MRoute mRoute, MTrip mTrip, GTrip gTrip, GSpec gtfs) {
		if (mRoute.id == 4l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(POWELL, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(UBC, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 6l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(DOWNTOWN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 7l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(DUNBAR, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 8l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(DOWNTOWN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 9l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(COMM_L_BDWAY_STN_BOUNDARY, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(GRANVILLE_ALMA_UBC, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 10l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(DOWNTOWN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(GRANVILLE, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 14l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(HASTINGS_DOWNTOWN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(UBC, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 16l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(ARBUTUS, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 17l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(DOWNTOWN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 19l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(METROTOWN_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(STANLEY_PK, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 20l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(DOWNTOWN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(VICTORIA, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 22l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(KNIGHT, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(MACDONALD, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 25l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(BRENTWOOD_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(UBC, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 27l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(KOOTENAY_LOOP, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 28l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(PHIBBS_EXCH, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(JOYCE_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 41l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(JOYCE_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(CROWN_UBC, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 49l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(METROTOWN_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(UBC, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 99l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(COMM_L_BDWAY_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(UBC, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 104l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(ANNACIS_ISL, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 110l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(METROTOWN_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 112l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(EDMONDS_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 130l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.NORTH);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.SOUTH);
				return;
			}
		} else if (mRoute.id == 135l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(SFU, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 160l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(PORT_COQUITLAM_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(VANCOUVER, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 209l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(VANCOUVER, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 210l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(UPPER_LYNN_VLY, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(VANCOUVER, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 227l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(PHIBBS_EXCH, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 228l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(LONSDALE_QUAY, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 230l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(UPPER_LONSDALE, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 232l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(GROUSE_MTN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 236l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(GROUSE_MTN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 239l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(CAPILANO_U, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(PK_ROYAL, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 240l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(VANCOUVER, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 242l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(UPPER_LONSDALE, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 246l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(LONSDALE_QUAY, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(VANCOUVER, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 247l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(UPPER_CAPILANO, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 250l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(VANCOUVER, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(HORSESHOE_BAY, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 253l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(VANCOUVER_PK_ROYAL, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 254l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(BRITISH_PROPERTIES, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(VANCOUVER_PK_ROYAL, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 255l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(CAPILANO_U, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(DUNDARAVE, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 256l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(WHITBY_ESTS, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(SPURAWAY_PK_ROYAL, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 257l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(VANCOUVER, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(HORSESHOE_BAY, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 259l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(HORSESHOE_BAY, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 314l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(SUNBURY, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 320l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(LANGLEY_CTR, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 321l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(NEWTON_EXCH_WHITE_ROCK, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 323l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(NEWTON_EXCH, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 329l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 335l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(NEWTON_EXCH, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 351l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(CR_BEACH, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 401l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(GDN_CITY, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(ONE_RD, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 403l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(BRIDGEPORT_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 404l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(BRIGHOUSE_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(FOUR_RD, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 403l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(FIVE_RD, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 407l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(BRIDGEPORT_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(GILBERT, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 410l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(_22ND_ST_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(RAILWAY, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 501l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(LANGLEY_CTR, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 503l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 555l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(CARVOLTH_EXCH, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(LOUGHEED_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 601l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.NORTH);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.SOUTH);
				return;
			}
		} else if (mRoute.id == 603l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(BRIDGEPORT_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 604l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(BRIDGEPORT_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 606l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.EAST);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.WEST);
				return;
			}
		} else if (mRoute.id == 608l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.EAST);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.WEST);
				return;
			}
		} else if (mRoute.id == 604l) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(LADNER_EXCH, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 701l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(MAPLE_RDG_EAST, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == 804l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.EAST);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.WEST);
				return;
			}
		} else if (mRoute.id == 807l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.NORTH);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.SOUTH);
				return;
			}
		} else if (mRoute.id == 828l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.NORTH);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.SOUTH);
				return;
			}
		} else if (mRoute.id == 855l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.EAST);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.WEST);
				return;
			}
		} else if (mRoute.id == 861l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.NORTH);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.SOUTH);
				return;
			}
		} else if (mRoute.id == 867l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.NORTH);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.SOUTH);
				return;
			}
		} else if (mRoute.id == 880l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.EAST);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.WEST);
				return;
			}
		} else if (mRoute.id == 881l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.EAST);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.WEST);
				return;
			}
		} else if (mRoute.id == 895l) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignDirection(MDirectionType.EAST);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignDirection(MDirectionType.WEST);
				return;
			}
		} else if (mRoute.id == RID_SW_C + 12) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(LIONS_BAY, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_C + 25) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(IOCO, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_C + 26) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(BELCARRA, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(PT_MOODY_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_C + 28) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(PT_MOODY_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_C + 36) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(POCO_STN, gTrip.direction_id);
				return;
			} else if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(POCO_SOUTH, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_C + 41) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(MAPLE_MDWS_STN_MEADOWTOWN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_C + 43) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(MAPLE_MDWS_STN_MEADOWTOWN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_C + 44) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(MAPLE_MDWS_STN_MEADOWTOWN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_C + 45) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(HANEY_PL, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_C + 46) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(ALBION, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_N + 10) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(DOWNTOWN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_N + 19) {
			if (gTrip.direction_id == 0) {
				mTrip.setHeadsignString(SURREY_CENTRAL_STN, gTrip.direction_id);
				return;
			}
		} else if (mRoute.id == RID_SW_N + 24) {
			if (gTrip.direction_id == 1) {
				mTrip.setHeadsignString(DOWNTOWN, gTrip.direction_id);
				return;
			}
		}
		mTrip.setHeadsignString(cleanTripHeadsign(gTrip.trip_headsign), gTrip.direction_id);
	}

	private static final Pattern STARTS_WITH_QUOTE = Pattern.compile("(^\")", Pattern.CASE_INSENSITIVE);

	private static final Pattern ENDS_WITH_QUOTE = Pattern.compile("(\"[;]?[\\s]?$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern ENDS_WITH_VIA = Pattern.compile("(via.*$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_ROUTE = Pattern.compile("(^[0-9A-Z]{1,4}[\\s]{1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern TO = Pattern.compile("((^|\\s){1}(to)[\\s]+)", Pattern.CASE_INSENSITIVE);

	private static final String SPACE = " ";

	private static final Pattern EXPRESS = Pattern.compile("((^|\\s){1}(\\- )?(express)( \\-)?(\\s|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern SPECIAL = Pattern.compile("((^|\\s){1}(\\- )?(special)( \\-)?(\\s|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern ONLY = Pattern.compile("((^|\\s){1}(\\- )?(only)( \\-)?(\\s|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern NIGHTBUS = Pattern.compile("((^|\\s){1}(nightbus)(\\s|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern EXCHANGE = Pattern.compile("((^|\\s){1}(exchange)(\\s|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String EXCHANGE_REPLACEMENT = "$2Exch$4"; // like in GTFS

	private static final Pattern ENDS_WITH_B_LINE = Pattern.compile("((^|\\s){1}(\\- b\\-line)(\\s|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern CLEAN_SLASHES = Pattern.compile("(\\S)[\\s]*[/][\\s]*(\\S)");
	private static final String CLEAN_SLASHES_REPLACEMENT = "$1 / $2";

	@Override
	public String cleanTripHeadsign(String tripHeadsign) {
		tripHeadsign = tripHeadsign.toLowerCase(Locale.ENGLISH);
		tripHeadsign = CLEAN_SLASHES.matcher(tripHeadsign).replaceAll(CLEAN_SLASHES_REPLACEMENT);
		tripHeadsign = STARTS_WITH_QUOTE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = ENDS_WITH_QUOTE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = STARTS_WITH_ROUTE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = ENDS_WITH_VIA.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = ENDS_WITH_B_LINE.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = TO.matcher(tripHeadsign).replaceAll(SPACE);
		tripHeadsign = AND.matcher(tripHeadsign).replaceAll(AND_REPLACEMENT);
		tripHeadsign = EXCHANGE.matcher(tripHeadsign).replaceAll(EXCHANGE_REPLACEMENT);
		tripHeadsign = NIGHTBUS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = EXPRESS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = SPECIAL.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = ONLY.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = CleanUtils.cleanNumbers(tripHeadsign);
		tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
		return CleanUtils.cleanLabel(tripHeadsign);
	}

	private static final Pattern AND = Pattern.compile("( and )", Pattern.CASE_INSENSITIVE);
	private static final String AND_REPLACEMENT = " & ";

	private static final Pattern AT = Pattern.compile("([\\s]+(at|fs|ns)[\\s]+)", Pattern.CASE_INSENSITIVE);
	private static final String AT_REPLACEMENT = SLASH;

	private static final Pattern STARTS_WITH_BOUND = Pattern.compile("(^(eb|wb|sb|nb) )", Pattern.CASE_INSENSITIVE);

	private static final Pattern UNLOADING = Pattern.compile("(unloading( only)?$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern ENDS_WITH_DASHES = Pattern.compile("([\\-]+$)", Pattern.CASE_INSENSITIVE);

	@Override
	public String cleanStopName(String gStopName) {
		gStopName = gStopName.toLowerCase(Locale.ENGLISH);
		gStopName = STARTS_WITH_BOUND.matcher(gStopName).replaceAll(StringUtils.EMPTY);
		gStopName = AT.matcher(gStopName).replaceAll(AT_REPLACEMENT);
		gStopName = AND.matcher(gStopName).replaceAll(AND_REPLACEMENT);
		gStopName = UNLOADING.matcher(gStopName).replaceAll(StringUtils.EMPTY);
		gStopName = ENDS_WITH_DASHES.matcher(gStopName).replaceAll(StringUtils.EMPTY);
		gStopName = EXCHANGE.matcher(gStopName).replaceAll(EXCHANGE_REPLACEMENT);
		gStopName = CleanUtils.cleanStreetTypes(gStopName);
		return CleanUtils.cleanLabel(gStopName);
	}

	@Override
	public int getStopId(GStop gStop) {
		if (!StringUtils.isEmpty(gStop.stop_code) && Utils.isDigitsOnly(gStop.stop_code)) {
			return Integer.parseInt(gStop.stop_code); // using stop code as stop ID
		}
		return 1000000 + Integer.parseInt(gStop.stop_id);
	}
}
