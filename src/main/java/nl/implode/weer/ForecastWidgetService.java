package nl.implode.weer;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

public class ForecastWidgetService extends Service {

    @Override
    public void onStart(Intent intent, int startId) {
        handleStart(intent, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleStart(intent, startId);
        return START_NOT_STICKY;
    }

    private void processForecasts(Context context, JSONObject forecast, RemoteViews views, Boolean useCelsius, Boolean useFahrenheit) {
        JSONObject days = new JSONObject();
        try {
            //only update view when we have new forecast data, preventing empty results
            if (forecast.has("list") && forecast.getJSONArray("list").length() > 0) {
                Calendar cal = Calendar.getInstance();
                Date updateTime = cal.getTime();
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.GERMANY);
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
                    SimpleDateFormat sdfDate = new SimpleDateFormat("EEEE d MMMM");
                    String day = sdfDate.format(cal.getTime());
                    if (!days.has(day)) {
                        dayNum++;
                        days.put(day, new JSONArray());
                    }
                    if (dayNum == 2) {
                        // gather times of second day
                        SimpleDateFormat sdfTime = new SimpleDateFormat("H:mm");
                        String time = sdfTime.format(cal.getTime());
                        times[timeNum] = time;
                        timeNum++;
                    }
                    days.getJSONArray(day).put(list.getJSONObject(i));
                }

                // remove old forecasts
                views.removeAllViews(R.id.widgetForecasts);

                // add times
                for (int l=0; l<times.length; l++) {
                    RemoteViews timeView = new RemoteViews(context.getPackageName(), R.layout.times);
                    timeView.setTextViewText(R.id.time, times[l]);
                    views.addView(R.id.widgetForecasts, timeView);
                }

                Iterator<String> keys = days.keys();
                Integer maxDays = 4;
                Integer numDays = 0;
                while (keys.hasNext() && numDays < maxDays) {
                    numDays++;
                    String dayName = (String) keys.next();
                    JSONArray dayForecasts = days.getJSONArray(dayName);
                    // add tablerow with just day name
                    RemoteViews dayLineView = new RemoteViews(context.getPackageName(), R.layout.day);
                    dayLineView.setTextViewText(R.id.day, dayName);
                    views.addView(R.id.widgetForecasts, dayLineView);

                    for (int n = 0; n < (8-dayForecasts.length()); n++) {
                        // add empty forecast
                        RemoteViews forecastView = new RemoteViews(context.getPackageName(), R.layout.forecast);
                        views.addView(R.id.widgetForecasts, forecastView);
                    }

                    for (int j = 0; j < dayForecasts.length(); j++) {
                        JSONObject dayForecast = dayForecasts.getJSONObject(j);
                        Double temp = Double.valueOf(dayForecast.getJSONObject("main").getString("temp"));
                        if (useCelsius) {
                            temp = temp - 273.15;
                        }
                        if (useFahrenheit) {
                            temp = 9/5*(temp - 273.15) + 32;
                        }

                        RemoteViews forecastView = new RemoteViews(context.getPackageName(), R.layout.forecast);
                        forecastView.setTextViewText(R.id.forecast_temp, String.valueOf(Math.round(temp)) + (char) 0x00B0);
                        if (Math.round(temp) < 0) {
                            forecastView.setTextColor(R.id.forecast_temp, Color.BLUE);
                        } else {
                            forecastView.setTextColor(R.id.forecast_temp, Color.RED);
                        }
                        String rain = "";
                        if (dayForecast.has("rain") && dayForecast.getJSONObject("rain").has("3h")) {
                            String sRain = dayForecast.getJSONObject("rain").getString("3h");
                            Float fRain = Float.valueOf(sRain);
                            String lessThan = "";
                            if (fRain < 0.1) {
                                lessThan = "<";
                                fRain = new Float(0.1);
                            }
                            rain = lessThan + String.format("%.1f", fRain) + " mm";
                        }
                        if (dayForecast.has("snow") && dayForecast.getJSONObject("snow").has("3h")) {
                            String sSnow = dayForecast.getJSONObject("snow").getString("3h");
                            Float fSnow = Float.valueOf(sSnow);
                            String lessThan = "";
                            if (fSnow < 0.1) {
                                lessThan = "<";
                                fSnow = new Float(0.1);
                            }
                            rain = (char) 0x2746 + " " + lessThan + String.format("%.1f", fSnow) + " mm";
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

                            wind = (char) 0x2332 + " " + context.getResources().getString(context.getResources().getIdentifier(dir, "string", context.getPackageName())) + " " + bft;

                        }
                        forecastView.setTextViewText(R.id.forecast_wind, wind);

                        if (dayForecast.has("weather") && dayForecast.getJSONArray("weather").length()>0) {
                            JSONArray weather = dayForecast.getJSONArray("weather");
                            if (weather.getJSONObject(0).has("icon")) {
                                String icon = "icon" + weather.getJSONObject(0).getString("icon");
                                forecastView.setImageViewResource(R.id.forecast_icon, context.getResources().getIdentifier(icon, "drawable", context.getPackageName()));
                            }
                        }

                        views.addView(R.id.widgetForecasts, forecastView);
                    }
                }
            }
        }catch(Exception e) {
            e.printStackTrace();
        } finally {

        }

    }

    private void handleStart(Intent intent, int startId) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this
                .getApplicationContext());

        int[] allWidgetIds = intent
                .getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
        Context context = this.getApplicationContext();

        ComponentName widgetComponentName = new ComponentName(context, ForecastWidget.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponentName);

        for (int widgetId : allWidgetIds) {
            CharSequence stationName = ForecastWidgetConfigureActivity.loadPref(context, "stationName", widgetId);
            CharSequence stationCountry = ForecastWidgetConfigureActivity.loadPref(context, "stationCountry", widgetId);
            String stationId = ForecastWidgetConfigureActivity.loadPref(context, "stationId", widgetId);
            Boolean useCelsius = true;
            Boolean useFahrenheit = false;

            JSONObject forecast = new JSONObject();
            WeatherStationsDatabase weatherStationsDatabase = new WeatherStationsDatabase(context);
            WeatherStation weatherStation = null;
            if (!stationId.isEmpty()) {
                weatherStation = weatherStationsDatabase.findWeatherStation(Integer.valueOf(stationId));
                forecast = weatherStation.get5DayForecast();
            }

            // Construct the RemoteViews object
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.forecast_widget);
            views.setTextViewText(R.id.stationName, stationName);
            views.setTextViewText(R.id.stationCountry, stationCountry);

            processForecasts(context, forecast, views, useCelsius, useFahrenheit);

            Intent clickIntent = new Intent(context, ForecastWidget.class);
            clickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                    0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            views.setOnClickPendingIntent(R.id.forecast_widget, pendingIntent);

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(widgetId, views);
        }
        stopSelf();

        super.onStart(intent, startId);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
