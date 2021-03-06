package nl.implode.weer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import com.skedgo.converter.TimezoneMapper;

public class ForecastWidgetService extends JobIntentService {

    public static final int JOB_ID = 1;

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, ForecastWidgetService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        handleStart(intent);
    }

    private Context gContext;
    private AppWidgetManager appWidgetManager;

    protected boolean dark;

    private int getLayout(String id) {
        switch(id){
            case "forecast_widget":
                return dark ? R.layout.forecast_widget_dark : R.layout.forecast_widget;
            case "day":
                return dark ? R.layout.day_dark : R.layout.day;
            case "forecast":
                return dark ? R.layout.forecast_dark : R.layout.forecast;
            case "times":
                return dark ? R.layout.times_dark : R.layout.times;
            case "color_temp":
                return dark ? 0xFFe91e63 : Color.RED;
            case "color_temp_freezing":
                return dark ? 0xFF7dc6bf : 0xFF3F51B5;
        }
        return 0;
    }

    private String getIconImage(String iconCode) {
        switch(iconCode) {
            case "01d": return "icon_32";
            case "02d": return "icon_30";
            case "03d": return "icon_28";
            case "04d": return "icon_26";
            case "09d": return "icon_12";
            case "10d": return "icon_39";
            case "11d": return "icon_37";
            case "13d": return "icon_41";
            case "50d": return "icon_20";

            case "01n": return "icon_31";
            case "02n": return "icon_33";
            case "03n": return "icon_27";
            case "04n": return "icon_26";
            case "09n": return "icon_12";
            case "10n": return "icon_45";
            case "11n": return "icon_47";
            case "13n": return "icon_46";
            case "50n": return "icon_20";

            case "r":  return "icon_12";
            case "sn50": return "icon_14";
            case "t50":  return "icon_35";
            case "w50":  return "icon_23";
        }
        return "";
    }

    private void processForecasts(JSONObject forecast, Integer widgetId) {
        Integer maxDays = 4;
        CharSequence widgetStyle = ForecastWidgetConfigureActivity.loadPref(gContext, "widgetStyle", widgetId);
        dark = widgetStyle.equals("dark");
        RemoteViews views = new RemoteViews(gContext.getPackageName(), getLayout("forecast_widget"));
        CharSequence stationName = ForecastWidgetConfigureActivity.loadPref(gContext, "stationName", widgetId);
        CharSequence stationCountry = ForecastWidgetConfigureActivity.loadPref(gContext, "stationCountry", widgetId);

        Bundle options = appWidgetManager.getAppWidgetOptions(widgetId);
        Integer minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        Integer maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);

        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            // portrait: minWidth x maxHeight
            maxDays = (int) Math.floor((maxHeight-40)/90);
        }

        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // landscape: maxWidth x minHeight
            maxDays = (int) Math.floor((minHeight-40)/90);
        }

        views.setTextViewText(R.id.stationName, stationName);
        views.setTextViewText(R.id.stationCountry, stationCountry);

        JSONObject days = new JSONObject();
        // rainScale (0 mm, 1 inch)
        // tempScale (0 Celsius, 1 Fahrenheit, 2 Kelvin)
        // time (0 24h, 1 am/pm)
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        Boolean useLocalTime = sharedPrefs.getBoolean("use_local_time", false);
        String prefTime = sharedPrefs.getString("time", "0");
        String prefTempScale = sharedPrefs.getString("temp_scale", "0");
        String prefRainScale = sharedPrefs.getString("rain_scale", "0");
        String timeZone = "";

        try {
            if (forecast.has("city") && forecast.getJSONObject("city").has("coord")) {
                Double lat = forecast.getJSONObject("city").getJSONObject("coord").getDouble("lat");
                Double lon = forecast.getJSONObject("city").getJSONObject("coord").getDouble("lon");
                timeZone = TimezoneMapper.latLngToTimezoneString(lat, lon);
            }
            //only update view when we have new forecast data, preventing empty results
            if (forecast.has("list") && forecast.getJSONArray("list").length() > 0) {
                Calendar cal = Calendar.getInstance();
                Date updateTime = cal.getTime();
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.GERMANY);
                if (prefTime.equals("1")) {
                    sdf = new SimpleDateFormat("KK:mm a");
                }
                String lastUpdate = sdf.format(updateTime);

                views.setTextViewText(R.id.updateTime, lastUpdate);


                JSONArray list = forecast.getJSONArray("list");
                String[] times = {
                        "0:00", "3:00","6:00","9:00","12:00","15:00","18:00","21:00"
                };
                Integer dayNum = 0;
                Integer timeNum = 0;
                for (int i = 0; i < list.length(); i++) {
                    cal.setTimeInMillis(list.getJSONObject(i).getInt("dt") * 1000L);
                    SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
                    if (useLocalTime) {
                        sdfDate.setTimeZone(TimeZone.getTimeZone(timeZone));
                    }
                    String day = sdfDate.format(cal.getTime());
                    if (!days.has(day)) {
                        dayNum++;
                        days.put(day, new JSONArray());
                    }
                    if (dayNum == 2) {
                        // gather times of second day
                        SimpleDateFormat sdfTime = new SimpleDateFormat("H:mm");
                        if (prefTime.equals("1")) {
                            sdfTime = new SimpleDateFormat("K:mma");
                        }
                        if (useLocalTime) {
                            sdfTime.setTimeZone(TimeZone.getTimeZone(timeZone));
                        }
                        String time = sdfTime.format(cal.getTime());
                        time = time.replace("a.m.","").replace("p.m.","p");
                        time = time.replace("AM","").replace("PM","p");
                        time = time.replace("A.M.","").replace("P.M.","p");
                        times[timeNum] = time;
                        timeNum++;
                    }
                    days.getJSONArray(day).put(list.getJSONObject(i));
                }

                // remove old forecasts
                views.removeAllViews(R.id.widgetForecasts);

                // add times
                for (int l=0; l<times.length; l++) {
                    RemoteViews timeView = new RemoteViews(gContext.getPackageName(), getLayout("times"));
                    timeView.setTextViewText(R.id.time, times[l]);
                    views.addView(R.id.widgetForecasts, timeView);
                }

                Iterator<String> keys = days.keys();
                Integer numDays = 0;
                while (keys.hasNext() && numDays < maxDays) {
                    numDays++;
                    String dayName = (String) keys.next();
                    SimpleDateFormat sdfDate = new SimpleDateFormat("EEEE d MMMM");
                    SimpleDateFormat keyDate = new SimpleDateFormat("yyyy-MM-dd");

                    String dayLabel = sdfDate.format(keyDate.parse(dayName));
                    JSONArray dayForecasts = days.getJSONArray(dayName);
                    // add tablerow with just day name
                    RemoteViews dayLineView = new RemoteViews(gContext.getPackageName(), getLayout("day"));
                    dayLineView.setTextViewText(R.id.day, dayLabel);
                    views.addView(R.id.widgetForecasts, dayLineView);

                    if (numDays < 2) {
                        for (int n = 0; n < (8 - dayForecasts.length()); n++) {
                            // add empty forecast
                            RemoteViews forecastView = new RemoteViews(gContext.getPackageName(), getLayout("forecast"));
                            views.addView(R.id.widgetForecasts, forecastView);
                        }
                    }

                    for (int j = 0; j < dayForecasts.length(); j++) {
                        JSONObject dayForecast = dayForecasts.getJSONObject(j);
                        Double temp = Double.valueOf(dayForecast.getJSONObject("main").getString("temp"));
                        Boolean isFreezing = temp < 273.15;

                        String tempSuffix = "" + (char) 0x00B0;
                        if (prefTempScale.equals("0")) {
                            // celsius
                            temp = temp - 273.15;
                        }
                        if (prefTempScale.equals("1")) {
                            // fahrenheit
                            Double factor = (double)9/(double)5;
                            temp = factor * (temp - 273.15) + 32;
                        }
                        if (prefTempScale.equals("2")) {
                            // kelvin
                            tempSuffix = "" + (char) 0x004B;
                        }

                        int tempColor = getLayout(isFreezing ? "color_temp_freezing" : "color_temp");

                        RemoteViews forecastView = new RemoteViews(gContext.getPackageName(), getLayout("forecast"));
                        forecastView.setTextViewText(R.id.forecast_temp, String.valueOf(Math.round(temp)) + tempSuffix);

                        forecastView.setTextColor(R.id.forecast_temp, tempColor);
                        String rain = "";
                        if (dayForecast.has("rain") && dayForecast.getJSONObject("rain").has("3h")) {
                            String sRain = dayForecast.getJSONObject("rain").getString("3h");
                            Float fRain = Float.valueOf(sRain);
                            String postFix = " ㎜";
                            if (prefRainScale.equals("1")) {
                                // inches
                                postFix = "\"";
                                fRain = fRain / new Float(2.54);
                            }
                            String lessThan = "";
                            if (fRain < 0.1) {
                                lessThan = "<";
                                fRain = new Float(0.1);
                            }
                            rain = lessThan + String.format("%.1f", fRain) + postFix;
                        }

                        if (dayForecast.has("snow") && dayForecast.getJSONObject("snow").has("3h")) {
                            String sSnow = dayForecast.getJSONObject("snow").getString("3h");
                            Float fSnow = Float.valueOf(sSnow);
                            String postFix = " ㎜";
                            if (prefRainScale.equals("1")) {
                                // inches
                                postFix = "\"";
                                fSnow = fSnow / new Float(2.54);
                            }
                            String lessThan = "";
                            if (fSnow < 0.1) {
                                lessThan = "<";
                                fSnow = new Float(0.1);
                            }
                            rain = lessThan + String.format("%.1f", fSnow) + postFix;
                        }
                        forecastView.setTextViewText(R.id.forecast_rain, rain);

                        String wind = "";
                        if (dayForecast.has("wind") && dayForecast.getJSONObject("wind").has("speed") && dayForecast.getJSONObject("wind").has("deg")) {
                            String speed = dayForecast.getJSONObject("wind").getString("speed");
                            String deg = dayForecast.getJSONObject("wind").getString("deg");
                            Float fSpeed = Float.valueOf(speed);
                            Float fDeg = Float.valueOf(deg);
                            String bft = "0";
                            if (fSpeed >= 0.3 && fSpeed < 1.6) { bft = "1"; }
                            if (fSpeed >= 1.6 && fSpeed < 3.4) { bft = "2"; }
                            if (fSpeed >= 3.4 && fSpeed < 5.5) { bft = "3"; }
                            if (fSpeed >= 5.5 && fSpeed < 8.0) { bft = "4"; }
                            if (fSpeed >= 8.0 && fSpeed < 10.8) { bft = "5"; }
                            if (fSpeed >= 10.8 && fSpeed < 13.9) { bft = "6"; }
                            if (fSpeed >= 13.9 && fSpeed < 17.2) { bft = "7"; }
                            if (fSpeed >= 17.2 && fSpeed < 20.8) { bft = "8"; }
                            if (fSpeed >= 20.8 && fSpeed < 24.5) { bft = "9"; }
                            if (fSpeed >= 24.5 && fSpeed < 28.5) { bft = "10"; }
                            if (fSpeed >= 28.5 && fSpeed < 32.7) { bft = "11"; }
                            if (fSpeed >= 32.7) { bft = "12"; }

                            String dir = "wind_n";
                            if (fDeg >= 22.5 && fDeg < 67.5) { dir = "wind_ne"; }
                            if (fDeg >= 67.5 && fDeg < 112.5) { dir = "wind_e"; }
                            if (fDeg >= 112.5 && fDeg < 157.5) { dir = "wind_se"; }
                            if (fDeg >= 157.5 && fDeg < 202.5) { dir = "wind_s"; }
                            if (fDeg >= 202.5 && fDeg < 247.5) { dir = "wind_sw"; }
                            if (fDeg >= 247.5 && fDeg < 292.5) { dir = "wind_w"; }
                            if (fDeg >= 292.5 && fDeg < 337.5) { dir = "wind_nw"; }

                            wind = (char) 0x2332 + " " + gContext.getResources().getString(gContext.getResources().getIdentifier(dir, "string", gContext.getPackageName())) + " " + bft;

                        }
                        forecastView.setTextViewText(R.id.forecast_wind, wind);

                        if (dayForecast.has("weather") && dayForecast.getJSONArray("weather").length()>0) {
                            JSONArray weather = dayForecast.getJSONArray("weather");
                            if (weather.getJSONObject(0).has("icon")) {
                                String iconCode = weather.getJSONObject(0).getString("icon");
                                String icon = getIconImage(iconCode);
                                forecastView.setImageViewResource(R.id.forecast_icon, gContext.getResources().getIdentifier(icon, "drawable", gContext.getPackageName()));
                            }
                        }

                        views.addView(R.id.widgetForecasts, forecastView);
                    }

                    if (numDays > 1) {
                        for (int n = 0; n < (8 - dayForecasts.length()); n++) {
                            // add empty forecast
                            RemoteViews forecastView = new RemoteViews(gContext.getPackageName(), getLayout("forecast"));
                            views.addView(R.id.widgetForecasts, forecastView);
                        }
                    }
                }
            }

            Intent clickIntent = new Intent(gContext, ForecastWidget.class);
            clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

            int[] appWidgetIds = new int[] {widgetId};
            clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(gContext,
                    widgetId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            views.setOnClickPendingIntent(R.id.widgetForecasts, pendingIntent);

            Class configurationClass = ForecastWidgetConfigureActivity.class;
            if (dark) {
                configurationClass = ForecastWidgetDarkConfigureActivity.class;
            }
            Intent configurationIntent = new Intent(gContext, configurationClass);

            // Create a extra giving the App Widget Id
            configurationIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);

            // Create a pending intent giving configurationIntent as parameter
            PendingIntent configurationPendingIntent = PendingIntent.getActivity(gContext,
                    widgetId, configurationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            // Here we fecth the layout item and give it a action

            // Setting onClick event that will lauch ConfigurationActivity
            views.setOnClickPendingIntent(R.id.settings, configurationPendingIntent);

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(widgetId, views);
        }catch(Exception e) {
            e.printStackTrace();
        } finally {

        }
    }

    private void handleStart(Intent intent) {
        appWidgetManager = AppWidgetManager.getInstance(this
                .getApplicationContext());

        int[] allWidgetIds = intent
                .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
        gContext = this.getApplicationContext();

        for (final int widgetId : allWidgetIds) {
            final String stationId = ForecastWidgetConfigureActivity.loadPref(gContext, "stationId", widgetId);
            new Thread() {
                public void run() {
                    Looper.prepare();
                    JSONObject forecast = new JSONObject();
                    WeatherStationsDatabase weatherStationsDatabase = new WeatherStationsDatabase(gContext);
                    WeatherStation weatherStation = null;
                    if (!stationId.isEmpty()) {
                        weatherStation = weatherStationsDatabase.findWeatherStation(Integer.valueOf(stationId));
                        forecast = weatherStation.get5DayForecast();
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("forecast", forecast.toString());
                    bundle.putInt("widgetId", widgetId);
                    Message msg = new Message();
                    msg.setData(bundle);

                    Handler mHandler = new Handler(new Handler.Callback() {

                        @Override
                        public boolean handleMessage(Message msg) {
                            Bundle bundle = msg.getData();
                            String jsonForecast = bundle.getString("forecast");
                            Integer mWidgetId = bundle.getInt("widgetId");
                            JSONObject forecast;
                            try {
                                forecast = new JSONObject(jsonForecast);
                                processForecasts(forecast, mWidgetId);
                            }catch(Exception e) {
                                Log.d("weer", e.getMessage());
                            }
                            return false;
                        }
                    });
                    mHandler.sendMessage(msg);
                    Looper.loop();
                }
            }.start();
        }
        stopSelf();
    }
}
