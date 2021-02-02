package org.mtransit.parser.ca_vancouver_translink_bus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.ColorUtils;
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.MTLog;
import org.mtransit.parser.StringUtils;
import org.mtransit.parser.Utils;
import org.mtransit.parser.gtfs.data.GCalendar;
import org.mtransit.parser.gtfs.data.GCalendarDate;
import org.mtransit.parser.gtfs.data.GRoute;
import org.mtransit.parser.gtfs.data.GSpec;
import org.mtransit.parser.gtfs.data.GStop;
import org.mtransit.parser.gtfs.data.GTrip;
import org.mtransit.parser.mt.data.MAgency;
import org.mtransit.parser.mt.data.MRoute;
import org.mtransit.parser.mt.data.MTrip;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.mtransit.parser.StringUtils.EMPTY;

// http://www.translink.ca/en/Schedules-and-Maps/Developer-Resources.aspx
// http://www.translink.ca/en/Schedules-and-Maps/Developer-Resources/GTFS-Data.aspx
// http://mapexport.translink.bc.ca/current/google_transit.zip
// http://ns.translink.ca/gtfs/notifications.zip
// http://ns.translink.ca/gtfs/google_transit.zip
// http://gtfs.translink.ca/static/latest
public class VancouverTransLinkBusAgencyTools extends DefaultAgencyTools {

	public static void main(@Nullable String[] args) {
		if (args == null || args.length == 0) {
			args = new String[3];
			args[0] = "input/gtfs.zip";
			args[1] = "../../mtransitapps/ca-vancouver-translink-bus-android/res/raw/";
			args[2] = ""; // files-prefix
		}
		new VancouverTransLinkBusAgencyTools().start(args);
	}

	@Nullable
	private HashSet<Integer> serviceIdInts;

	@Override
	public void start(@NotNull String[] args) {
		MTLog.log("Generating TransLink bus data...");
		long start = System.currentTimeMillis();
		this.serviceIdInts = extractUsefulServiceIdInts(args, this, true);
		super.start(args);
		MTLog.log("Generating TransLink bus data... DONE in %s.", Utils.getPrettyDuration(System.currentTimeMillis() - start));
	}

	@Override
	public boolean excludingAll() {
		return this.serviceIdInts != null && this.serviceIdInts.isEmpty();
	}

	@Override
	public boolean excludeCalendar(@NotNull GCalendar gCalendar) {
		if (this.serviceIdInts != null) {
			return excludeUselessCalendarInt(gCalendar, this.serviceIdInts);
		}
		return super.excludeCalendar(gCalendar);
	}

	@Override
	public boolean excludeCalendarDate(@NotNull GCalendarDate gCalendarDates) {
		if (this.serviceIdInts != null) {
			return excludeUselessCalendarDateInt(gCalendarDates, this.serviceIdInts);
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
	public boolean excludeRoute(@NotNull GRoute gRoute) {
		if (EXCLUDE_RSN.contains(gRoute.getRouteShortName())) {
			return true;
		}
		return super.excludeRoute(gRoute);
	}

	@Override
	public boolean excludeTrip(@NotNull GTrip gTrip) {
		if (this.serviceIdInts != null) {
			return excludeUselessTripInt(gTrip, this.serviceIdInts);
		}
		return super.excludeTrip(gTrip);
	}

	@NotNull
	@Override
	public Integer getAgencyRouteType() {
		return MAgency.ROUTE_TYPE_BUS;
	}

	private static final String N = "N";
	private static final String R = "R";

	@Override
	public long getRouteId(@NotNull GRoute gRoute) {
		//noinspection deprecation
		return Long.parseLong(CleanUtils.cleanMergedID(gRoute.getRouteId())); // useful to match with GTFS real-time;
	}

	@Nullable
	@Override
	public String getRouteShortName(@NotNull GRoute gRoute) {
		String routeShortName = gRoute.getRouteShortName(); // used by real-time API
		if (Utils.isDigitsOnly(routeShortName)) { // used by real-time API
			routeShortName = String.valueOf(Integer.valueOf(routeShortName)); // used by real-time API
		} // used by real-time API
		return routeShortName; // used by real-time API
	}

	@NotNull
	@Override
	public String getRouteLongName(@NotNull GRoute gRoute) {
		return cleanRouteLongName(gRoute.getRouteLongNameOrDefault());
	}

	@NotNull
	private String cleanRouteLongName(@NotNull String gRouteLongName) {
		gRouteLongName = CleanUtils.toLowerCaseUpperCaseWords(Locale.ENGLISH, gRouteLongName, getIgnoredWords());
		gRouteLongName = CleanUtils.cleanSlashes(gRouteLongName);
		gRouteLongName = CleanUtils.cleanStreetTypes(gRouteLongName);
		return CleanUtils.cleanLabel(gRouteLongName);
	}

	private static final String AGENCY_COLOR_BLUE = "0761A5"; // BLUE (merge)

	private static final String AGENCY_COLOR = AGENCY_COLOR_BLUE;

	@NotNull
	@Override
	public String getAgencyColor() {
		return AGENCY_COLOR;
	}

	private static final String NIGHT_BUS_COLOR = "062F53"; // DARK BLUE (from PDF map)
	private static final String B_LINE_BUS_COLOR = "F46717"; // ORANGE (from PDF map)
	private static final String RAPID_BUS_COLOR = "199354"; // GREEN (from PDF map)

	private static final String B_LINE = "B-LINE";

	@Nullable
	@Override
	public String getRouteColor(@NotNull GRoute gRoute) {
		String gRouteColor = gRoute.getRouteColor();
		if (ColorUtils.WHITE.equalsIgnoreCase(gRouteColor)) {
			gRouteColor = null;
		}
		if (StringUtils.isEmpty(gRouteColor)) {
			final String rsn = gRoute.getRouteShortName();
			final String rln = gRoute.getRouteLongNameOrDefault();
			if (rsn.startsWith(N)) {
				return NIGHT_BUS_COLOR;
			}
			if (rsn.startsWith(R)) {
				return RAPID_BUS_COLOR;
			}
			if (rln.contains(B_LINE)) {
				return B_LINE_BUS_COLOR;
			}
			return null; // use agency color
		}
		return super.getRouteColor(gRoute);
	}

	@Override
	public void setTripHeadsign(@NotNull MRoute mRoute, @NotNull MTrip mTrip, @NotNull GTrip gTrip, @NotNull GSpec gtfs) {
		mTrip.setHeadsignString(
				cleanTripHeadsign(gTrip.getTripHeadsignOrDefault()),
				gTrip.getDirectionIdOrDefault()
		);
	}

	@Override
	public boolean directionFinderEnabled() {
		return true;
	}

	private static final Pattern BAY_ = Pattern.compile("((^|\\W)(b:\\w+)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String BAY_REPLACEMENT = "$2" + "$4";

	private static final Pattern BOUNDS_ = Pattern.compile("((^|\\W)(nb|sb|eb|wb)(\\W|$))", Pattern.CASE_INSENSITIVE);

	private static final Pattern FS_NS_ = Pattern.compile("((^|\\W)(fs|ns)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String FS_NS_REPLACEMENT = "$2" + "/" + "$4";

	@NotNull
	@Override
	public String cleanDirectionHeadsign(boolean fromStopName, @NotNull String directionHeadSign) {
		directionHeadSign = super.cleanDirectionHeadsign(fromStopName, directionHeadSign);
		if (fromStopName) {
			directionHeadSign = BAY_.matcher(directionHeadSign).replaceAll(BAY_REPLACEMENT);
			directionHeadSign = BOUNDS_.matcher(directionHeadSign).replaceAll(EMPTY);
			directionHeadSign = FS_NS_.matcher(directionHeadSign).replaceAll(FS_NS_REPLACEMENT);
			directionHeadSign = CleanUtils.cleanLabel(directionHeadSign);
		}
		return directionHeadSign;
	}

	@Override
	public boolean mergeHeadsign(@NotNull MTrip mTrip, @NotNull MTrip mTripToMerge) {
		throw new MTLog.Fatal("%d: Unexpected trips to merge %s & %s!", mTrip.getRouteId(), mTrip, mTripToMerge);
	}

	private static final Pattern STARTS_WITH_QUOTE = Pattern.compile("(^\")", Pattern.CASE_INSENSITIVE);

	private static final Pattern ENDS_WITH_QUOTE = Pattern.compile("(\"[;]?[\\s]?$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_ROUTE = Pattern.compile("(^[A-Z]?[0-9]{1,3}[\\s]+)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_SLASH = Pattern.compile("(^.* / )", Pattern.CASE_INSENSITIVE);

	private static final Pattern EXPRESS = Pattern.compile("(-?( express|express )-?)", Pattern.CASE_INSENSITIVE);

	private static final Pattern SPECIAL_ = Pattern.compile("(-?( special|special )-?)", Pattern.CASE_INSENSITIVE);

	private static final Pattern ONLY = Pattern.compile("(-?( only|only )-?)", Pattern.CASE_INSENSITIVE);

	private static final String PORT_COQUITLAM_SHORT = "PoCo";
	private static final Pattern PORT_COQUITLAM = Pattern.compile("((^|\\W)(port coquitlam|pt coquitlam|poco)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String PORT_COQUITLAM_REPLACEMENT = "$2" + PORT_COQUITLAM_SHORT + "$4";

	private static final String COQUITLAM_SHORT = "Coq";
	private static final Pattern COQUITLAM = Pattern.compile("((^|\\W)(coquitlam|coq)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String COQUITLAM_REPLACEMENT = "$2" + COQUITLAM_SHORT + "$4";

	private static final String STATION_SHORT = "Sta"; // see @CleanUtils
	private static final Pattern STATION = Pattern.compile("((^|\\W)(stn|sta|station)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String STATION_REPLACEMENT = "$2" + STATION_SHORT + "$4";

	private static final String PORT_SHORT = "Pt"; // like GTFS & real-time API
	private static final Pattern PORT = Pattern.compile("((^|\\W)(port)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String PORT_REPLACEMENT = "$2" + PORT_SHORT + "$4";

	private static final Pattern NIGHTBUS = Pattern.compile("((^|\\s)(nightbus)(\\s|$))", Pattern.CASE_INSENSITIVE);

	private static final String SURREY_SHORT = "Sry";
	private static final Pattern SURREY_ = Pattern.compile("((^|\\s)(surrey)(\\s|$))", Pattern.CASE_INSENSITIVE);
	private static final String SURREY_REPLACEMENT = "$2" + SURREY_SHORT + "$4";

	private static final Pattern ENDS_WITH_B_LINE = Pattern.compile("((^|\\s)(- )?(b-line)(\\s|$))", Pattern.CASE_INSENSITIVE);

	private static final String UBC = "UBC";
	private static final Pattern U_B_C = Pattern.compile("((^|\\W)(u b c)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String U_B_C_REPLACEMENT = "$2" + UBC + "$4";

	private static final String CENTRAL_SHORT = "Ctrl";
	private static final Pattern CENTRAL = Pattern.compile("((^|\\W)(central)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String CENTRAL_REPLACEMENT = "$2" + CENTRAL_SHORT + "$4";

	private static final String BRAID_STATION = "Braid " + STATION_SHORT;
	private static final Pattern BRAID_STATION_ = Pattern.compile("((^|\\W)(brad stn)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String BRAID_STATION_REPLACEMENT = "$2" + BRAID_STATION + "$4";

	private static final Pattern REMOVE_DASH = Pattern.compile("(^(\\s)*-(\\s)*|(\\s)*-(\\s)*$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern CLEAN_DASH = Pattern.compile("(\\s*-\\s*)", Pattern.CASE_INSENSITIVE);
	private static final String CLEAN_DASH_REPLACEMENT = "-";

	@NotNull
	@Override
	public String cleanTripHeadsign(@NotNull String tripHeadsign) {
		tripHeadsign = CleanUtils.toLowerCaseUpperCaseWords(Locale.ENGLISH, tripHeadsign, getIgnoredWords());
		tripHeadsign = CleanUtils.keepToAndRemoveVia(tripHeadsign);
		tripHeadsign = CleanUtils.cleanSlashes(tripHeadsign);
		tripHeadsign = STARTS_WITH_QUOTE.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = ENDS_WITH_QUOTE.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = STARTS_WITH_ROUTE.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = STARTS_WITH_SLASH.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = ENDS_WITH_B_LINE.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = CleanUtils.CLEAN_AND.matcher(tripHeadsign).replaceAll(CleanUtils.CLEAN_AND_REPLACEMENT);
		tripHeadsign = CleanUtils.CLEAN_AT.matcher(tripHeadsign).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		tripHeadsign = CENTRAL.matcher(tripHeadsign).replaceAll(CENTRAL_REPLACEMENT);
		tripHeadsign = U_B_C.matcher(tripHeadsign).replaceAll(U_B_C_REPLACEMENT);
		tripHeadsign = BRAID_STATION_.matcher(tripHeadsign).replaceAll(BRAID_STATION_REPLACEMENT);
		tripHeadsign = PORT_COQUITLAM.matcher(tripHeadsign).replaceAll(PORT_COQUITLAM_REPLACEMENT);
		tripHeadsign = COQUITLAM.matcher(tripHeadsign).replaceAll(COQUITLAM_REPLACEMENT);
		tripHeadsign = STATION.matcher(tripHeadsign).replaceAll(STATION_REPLACEMENT);
		tripHeadsign = SURREY_.matcher(tripHeadsign).replaceAll(SURREY_REPLACEMENT);
		tripHeadsign = PORT.matcher(tripHeadsign).replaceAll(PORT_REPLACEMENT);
		tripHeadsign = NIGHTBUS.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = EXPRESS.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = SPECIAL_.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = ONLY.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = REMOVE_DASH.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = CLEAN_DASH.matcher(tripHeadsign).replaceAll(CLEAN_DASH_REPLACEMENT);
		tripHeadsign = CleanUtils.fixMcXCase(tripHeadsign);
		tripHeadsign = CleanUtils.cleanNumbers(tripHeadsign);
		tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
		return CleanUtils.cleanLabel(tripHeadsign);
	}

	private static final Pattern FLAG_STOP = Pattern.compile("((^flagstop)[\\s]*(.*$))", Pattern.CASE_INSENSITIVE);
	private static final String FLAG_STOP_REPLACEMENT = "$3 ($2)";

	private static final Pattern UNLOADING = Pattern.compile("(unloading( only)?$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern ENDS_WITH_DASHES = Pattern.compile("([\\-]+$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_AT = Pattern.compile("(^@ )", Pattern.CASE_INSENSITIVE);

	private String[] getIgnoredWords() {
		return new String[]{
				"FS", "NS",
				"AM", "PM",
				"SW", "NW", "SE", "NE",
				"UBC", "SFU", "VCC",
		};
	}

	@NotNull
	@Override
	public String cleanStopName(@NotNull String gStopName) {
		gStopName = CleanUtils.toLowerCaseUpperCaseWords(Locale.ENGLISH, gStopName, getIgnoredWords());
		gStopName = CleanUtils.cleanSlashes(gStopName);
		gStopName = CleanUtils.cleanBounds(gStopName);
		gStopName = CleanUtils.fixMcXCase(gStopName);
		gStopName = CleanUtils.SAINT.matcher(gStopName).replaceAll(CleanUtils.SAINT_REPLACEMENT);
		gStopName = CleanUtils.CLEAN_AT.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		gStopName = CleanUtils.CLEAN_AND.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AND_REPLACEMENT);
		gStopName = FLAG_STOP.matcher(gStopName).replaceAll(FLAG_STOP_REPLACEMENT);
		gStopName = UNLOADING.matcher(gStopName).replaceAll(EMPTY);
		gStopName = ENDS_WITH_DASHES.matcher(gStopName).replaceAll(EMPTY);
		gStopName = STATION.matcher(gStopName).replaceAll(STATION_REPLACEMENT);
		gStopName = CENTRAL.matcher(gStopName).replaceAll(CENTRAL_REPLACEMENT);
		gStopName = STARTS_WITH_AT.matcher(gStopName).replaceAll(EMPTY);
		gStopName = CleanUtils.cleanStreetTypes(gStopName);
		return CleanUtils.cleanLabel(gStopName);
	}

	@Override
	public int getStopId(@NotNull GStop gStop) {
		return super.getStopId(gStop); // using stop ID as stop code (useful to match with GTFS real-time)
	}
}
