/*
 * Copyright (c) 2016 Lars Gaidzik & Lukas Mahr
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.hof.university.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.hof.university.app.Communication.RegisterLectures;
import de.hof.university.app.MainActivity;
import de.hof.university.app.R;
import de.hof.university.app.Util.Define;
import de.hof.university.app.Util.Log;
import de.hof.university.app.Util.MyString;
import de.hof.university.app.data.parser.Parser;
import de.hof.university.app.data.parser.ParserFactory;
import de.hof.university.app.data.parser.ParserFactory.EParser;
import de.hof.university.app.model.HofObject;
import de.hof.university.app.model.LastUpdated;
import de.hof.university.app.model.meal.Meal;
import de.hof.university.app.model.meal.Meals;
import de.hof.university.app.model.schedule.Changes;
import de.hof.university.app.model.schedule.LectureItem;
import de.hof.university.app.model.schedule.MySchedule;
import de.hof.university.app.model.schedule.Schedule;
import de.hof.university.app.model.settings.StudyCourse;
import de.hof.university.app.model.settings.StudyCourses;

/**
 *
 */
public class DataManager {

    public static final String TAG = "DataManager";

    // single instance of the Factories
    static final private DataConnector dataConnector = new DataConnector();

    private Schedule schedule;
    private MySchedule mySchedule;
    private Changes changes;
    private Meals meals;
    private StudyCourses studyCourses;

    private static final DataManager dataManager = new DataManager();


    public static DataManager getInstance() {
        return DataManager.dataManager;
    }

    private DataManager() {
    }

    public final ArrayList<Meal> getMeals(Context context, boolean forceRefresh) {
        Meals meals = this.getMeals(context);

        if (forceRefresh
                || (meals.getMeals().size() == 0)
                || (meals.getLastSaved() == null)
                || !cacheStillValid(meals, Define.MEAL_CACHE_TIME)) {
            final Parser parser = ParserFactory.create(EParser.MENU);
            final Calendar calendar = Calendar.getInstance();
            final String url = Define.URL_MEAL + calendar.get(Calendar.YEAR) + '-' + (calendar.get(Calendar.MONTH) + 1) + '-' + calendar.get(Calendar.DAY_OF_MONTH);
            final String xmlString = this.getData(url);


            // falls der String leer ist war ein Problem mit dem Internet
            if (xmlString.isEmpty()) {
                // prüfen ob es kein ForceRefreseh war, dann kann gecachtes zurück gegeben werden
                if (!forceRefresh && meals.getMeals().size() > 0) {
                    return meals.getMeals();
                } else {
                    // anderen falls null, damit dann die Fehlermeldung "Aktualisierung fehlgeschlagen" kommt
                    return null;
                }
            }

            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            final String[] params = {xmlString, sharedPreferences.getString("speiseplan_tarif", "1")};
            assert parser != null;

            ArrayList<Meal> tmpMeals = (ArrayList<Meal>) parser.parse(params);

            this.getMeals(context).setMeals(tmpMeals);

            this.getMeals(context).setLastSaved(new Date());
            saveObject(context, this.getMeals(context), Define.mealsFilename);
        }

        return this.getMeals(context).getMeals();
    }

    public final ArrayList<LectureItem> getSchedule(Context context, String language, String course, String semester,
                                                    String termTime, boolean forceRefresh) {
        Schedule schedule = this.getSchedule(context);

        if (forceRefresh
                || (schedule.getLectures().size() == 0)
                || (schedule.getLastSaved() == null)
                || !cacheStillValid(schedule, Define.SCHEDULE_CACHE_TIME)
                || !schedule.getCourse().equals(course)
                || !schedule.getSemester().equals(semester)
                || !schedule.getTermtime().equals(termTime)) {
            // Änderungen sollen neu geholt werden
            resetChangesLastSave(context);

            final Parser parser = ParserFactory.create(EParser.SCHEDULE);
            final String aString = String.format(Define.URL_SCHEDULE, MyString.URLReplaceWhitespace(course), MyString.URLReplaceWhitespace(semester), MyString.URLReplaceWhitespace(termTime));
            final String jsonString = this.getData(aString);

            // falls der String leer ist war ein Problem mit dem Internet
            if (jsonString.isEmpty()) {
                // prüfen ob es kein ForceRefreseh war, dann kann gecachtes zurück gegeben werden
                if (!forceRefresh && schedule.getLectures().size() > 0) {
                    return schedule.getLectures();
                } else {
                    // anderen falls null, damit dann die Fehlermeldung "Aktualisierung fehlgeschlagen" kommt
                    return null;
                }
            }

            final String[] params = {jsonString, language};
            assert parser != null;

            ArrayList<LectureItem> lectures = (ArrayList<LectureItem>) parser.parse(params);

            // Wenn der Server einen unvollständigen Stundenplan (nur halb so groß oder kleiner) liefert bringe die Fehlermedlung "Aktualisierung fehlgeschlagen"
            if (course.equals(schedule.getCourse()) && semester.equals(schedule.getSemester()) && termTime.equals(schedule.getTermtime()) && lectures.size() < (schedule.getLectures().size() / 2)) {
                return null;
            }

            this.getSchedule(context).setLectures(lectures);

            this.getSchedule(context).setCourse(course);
            this.getSchedule(context).setSemester(semester);
            this.getSchedule(context).setTermtime(termTime);
            this.getSchedule(context).setLastSaved(new Date());

            saveObject(context, this.getSchedule(context), Define.scheduleFilename);
        }

        return this.getSchedule(context).getLectures();
    }

    public final ArrayList<LectureItem> getMySchedule(Context context, String language,
                                                      boolean forceRefresh) {
        MySchedule mySchedule = this.getMySchedule(context);

        if (forceRefresh
                || (mySchedule.getLectures().size() == 0)
                || (mySchedule.getLastSaved() == null)
                || !cacheStillValid(mySchedule, Define.MYSCHEDULE_CACHE_TIME)
                || (mySchedule.getIds().size() != mySchedule.getLectures().size())
                ) {
            // Änderungen sollen neu geholt werden
            resetChangesLastSave(context);

            final Iterator<String> iterator = this.getMySchedule(context).getIds().iterator();
            String url = Define.URL_MYSCHEDULE;
            while (iterator.hasNext()) {
                try {
                    url += "&id[]=" + URLEncoder.encode(iterator.next(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            final Parser parser = ParserFactory.create(EParser.MYSCHEDULE);

            final String jsonString = this.getData(url);

            // falls der String leer ist war ein Problem mit dem Internet
            if (jsonString.isEmpty()) {
                // prüfen ob es kein ForceRefreseh war, dann kann gecachtes zurück gegeben werden
                if (!forceRefresh && mySchedule.getLectures().size() > 0) {
                    return mySchedule.getLectures();
                } else {
                    // anderen falls null, damit dann die Fehlermeldung "Aktualisierung fehlgeschlagen" kommt
                    return null;
                }
            }

            final String[] params = {jsonString, language};

            ArrayList<LectureItem> tmpMySchedule = (ArrayList<LectureItem>) parser.parse(params);

            // Wenn der Server einen unvollständigen Stundenplan (nur halb so groß oder kleiner) liefert bringe die Fehlermedlung "Aktualisierung fehlgeschlagen"
            if (tmpMySchedule.size() < (getMyScheduleSize(context) / 2)) {
                return null;
            }

            this.getMySchedule(context).setLectures(tmpMySchedule);
            this.getMySchedule(context).setLastSaved(new Date());

            this.saveObject(context, getMySchedule(context), Define.myScheduleFilename);
        }

        return this.getMySchedule(context).getLectures();
    }

    public final ArrayList<Object> getChanges(Context context, String course, String semester,
                                              String termTime, boolean forceRefresh) {
        Changes changes = this.getChanges(context);

        if (forceRefresh
                || (changes.getChanges().size() == 0)
                || (changes.getLastSaved() == null)
                || !cacheStillValid(changes, Define.CHANGES_CACHE_TIME)
                ) {
            final Iterator<String> iterator = this.getMySchedule(context).getIds().iterator();

            String url = Define.URL_CHANGES;

            if (!iterator.hasNext()) {
                url += "&stg=" + MyString.URLReplaceWhitespace(course);
                url += "&sem=" + MyString.URLReplaceWhitespace(semester);
                url += "&tt=" + MyString.URLReplaceWhitespace(termTime);
            } else {
                // Fügt die ID's der Vorlesungen hinzu die in Mein Stundenplan sind
                // dadurch werden nur Änderungen von Mein Stundenplan geholt
                while (iterator.hasNext()) {
                    try {
                        url += "&id[]=" + URLEncoder.encode(iterator.next(), "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }

            final Parser parser = ParserFactory.create(EParser.CHANGES);
            final String jsonString = this.getData(url);

            // falls der String leer ist war ein Problem mit dem Internet
            if (jsonString.isEmpty()) {
                // prüfen ob es kein ForceRefreseh war, dann kann gecachtes zurück gegeben werden
                if (!forceRefresh && changes.getChanges().size() > 0) {
                    return changes.getChanges();
                } else {
                    // anderen falls null, damit dann die Fehlermeldung "Aktualisierung fehlgeschlagen" kommt
                    return null;
                }
            }

            final String[] params = {jsonString};
            assert parser != null;

            ArrayList<Object> tmpChanges = (ArrayList<Object>) parser.parse(params);

            if (tmpChanges.size() > 0) {
                tmpChanges.add(new LastUpdated(context.getString(R.string.lastUpdated) + ": " + formatDate(new Date())));
            }

            this.getChanges(context).setChanges(tmpChanges);

            this.getChanges(context).setLastSaved(new Date());
            saveObject(context, this.getChanges(context), Define.changesFilename);
        }

        return this.getChanges(context).getChanges();
    }

    // es muss immer ein StudyCourse zurückgegeben werden.
    // wenn es aber
    public final ArrayList<StudyCourse> getCourses(final Context context, final String language,
                                                   final String termTime, boolean forceRefresh) {
        StudyCourses studyCourses = this.getStudyCourses(context);

        // Änderungen sollen neu geholt werden
        resetChangesLastSave(context);

        if (forceRefresh
                || (studyCourses.getCourses().size() == 0)
                || (studyCourses.getLastSaved() == null)
                || !cacheStillValid(studyCourses, Define.COURSES_CACHE_TIME)
                ) {
            final Parser parser = ParserFactory.create(EParser.COURSES);

            final String sTermType = MyString.URLReplaceWhitespace(termTime);
            final String sURL = String.format(Define.URL_STUDYCOURSE, sTermType);
            final String jsonString = this.getData(sURL);


            // falls der String leer ist war ein Problem mit dem Internet
            if (jsonString.isEmpty()) {
                // prüfen ob es kein ForceRefreseh war, dann kann gecachtes zurück gegeben werden
                if (!forceRefresh) {
                    return studyCourses.getCourses();
                } else {
                    // anderen falls null, damit dann die Fehlermeldung "Aktualisierung fehlgeschlagen" kommt
                    return null;
                }
            }

            final String[] params = {jsonString, language};
            assert parser != null;

            ArrayList<StudyCourse> tmpCourses = (ArrayList<StudyCourse>) parser.parse(params);

            this.getStudyCourses(context).setCourses(tmpCourses);

            this.getStudyCourses(context).setLastSaved(new Date());
            saveObject(context, this.getStudyCourses(context), Define.coursesFilename);
        }

        return this.getStudyCourses(context).getCourses();
    }

    // Änderungen sollen neu geholt werden
    private void resetChangesLastSave(Context context) {
        Changes changes = (Changes) readObject(context, Define.changesFilename);
        // Überprüfen ob Datei leer ist dann neu anlegen
        if (changes == null) {
            changes = new Changes();
        }
        // LastSaved zurücksetzten damit Änderungen neu geholt werden
        changes.setLastSaved(null);
        saveObject(context, changes, Define.changesFilename);
    }


    private String getData(String url) {
        return dataConnector.getStringFromUrl(url);
    }

    public final void addToMySchedule(final Context context, final LectureItem s) {
        this.getMySchedule(context).getIds().add(String.valueOf(s.getId()));
        this.saveObject(context, this.getMySchedule(context), Define.myScheduleFilename);
    }

    public final boolean myScheduleContains(final Context context, final LectureItem s) {
        return this.getMySchedule(context).getIds().contains(String.valueOf(s.getId()));
    }

    public final void deleteFromMySchedule(final Context context, final LectureItem s) {
        this.getMySchedule(context).getIds().remove(String.valueOf(s.getId()));
        LectureItem lectureToRemove = null;
        for (LectureItem li : this.getMySchedule(context).getLectures()) {
            if (li.getId().equals(s.getId())) {
                lectureToRemove = li;
            }
        }
        if (lectureToRemove != null) {
            this.getMySchedule(context).getLectures().remove(lectureToRemove);
        }
        this.saveObject(context, this.getMySchedule(context), Define.myScheduleFilename);
    }

    public final void addAllToMySchedule(final Context context, final Set<String> schedulesIds) {
        this.getMySchedule(context).getIds().addAll(schedulesIds);
        this.saveObject(context, this.getMySchedule(context), Define.myScheduleFilename);
    }

    public final void deleteAllFromMySchedule(final Context context) {
        this.getMySchedule(context).getIds().clear();
        this.getMySchedule(context).getLectures().clear();
        this.saveObject(context, this.getMySchedule(context), Define.myScheduleFilename);
    }

    // Getters
    // ---------------------------------------------------------------------------------------------

    private Schedule getSchedule(final Context context) {
        if (this.schedule == null) {
            Object optScheduleObj = readObject(context, Define.scheduleFilename);
            if (optScheduleObj != null) {
                this.schedule = (Schedule) optScheduleObj;
            } else {
                this.schedule = new Schedule();
            }
        }
        return this.schedule;
    }

    public Date getScheduleLastSaved() {
        return getSchedule(MainActivity.contextOfApplication).getLastSaved();
    }

    private MySchedule getMySchedule(final Context context) {
        if (this.mySchedule == null) {
            Object obtMyScheduleOpj = DataManager.readObject(context, Define.myScheduleFilename);
            if (obtMyScheduleOpj != null && obtMyScheduleOpj instanceof Set) {
                this.mySchedule = new MySchedule();
                this.mySchedule.setIds((Set<String>) obtMyScheduleOpj);
            } else if (obtMyScheduleOpj != null && obtMyScheduleOpj instanceof MySchedule) {
                this.mySchedule = (MySchedule) obtMyScheduleOpj;
            } else {
                this.mySchedule = new MySchedule();
            }
        }
        return this.mySchedule;
    }

    public final int getMyScheduleSize(final Context context) {
        return this.getMySchedule(context).getIds().size();
    }

    public Date getMyScheduleLastSaved() {
        return getMySchedule(MainActivity.contextOfApplication).getLastSaved();
    }

    private Changes getChanges(final Context context) {
        if (this.changes == null) {
            Object obtChangesObj = readObject(context, Define.changesFilename);
            if (obtChangesObj instanceof Meals) {
                this.changes = (Changes) obtChangesObj;
            } else {
                this.changes = new Changes();
            }
        }
        return this.changes;
    }

    public Date getChangesLastSaved() {
        return getChanges(MainActivity.contextOfApplication).getLastSaved();
    }

    private Meals getMeals(final Context context) {
        if (this.meals == null) {
            Object obtMealsObj = readObject(context, Define.mealsFilename);
            if (obtMealsObj instanceof Meals) {
                this.meals = (Meals) obtMealsObj;
            } else {
                this.meals = new Meals();
            }
        }
        return this.meals;
    }

    public Date getMealsLastSaved() {
        return getMeals(MainActivity.contextOfApplication).getLastSaved();
    }

    private StudyCourses getStudyCourses(final Context context) {
        if (this.studyCourses == null) {
            Object obtStudyCoursesObj = readObject(context, Define.mealsFilename);
            if (obtStudyCoursesObj instanceof StudyCourses) {
                this.studyCourses = (StudyCourses) obtStudyCoursesObj;
            } else {
                this.studyCourses = new StudyCourses();
            }
        }
        return this.studyCourses;
    }

    /**
     * formatiert ein Datum
     * @return dd.MM.yyyy HH:mm formatiertes Date
     */
    public String formatDate(Date date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        return simpleDateFormat.format(date);
    }

    // Saving and loading
    // ---------------------------------------------------------------------------------------------

    // this is the general method to serialize an object
    //
    private void saveObject(final Context context, Object object, final String filename) {
        try {
            final File file = new File(context.getFilesDir(), filename);
            final FileOutputStream fos = new FileOutputStream(file);
            final ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(object);
            os.close();
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Fehler beim Speichern des Objektes", e);
        }

        if (object instanceof Schedule || object instanceof MySchedule) {
            // Änderungen neu holen
            resetChangesLastSave(context);
            // Stundenplan registrieren
            registerFCMServer(context);
        }
    }

    // this is the general method to serialize an object
    private static Object readObject(final Context context, String filename) {
        Object result = null;
        try {
            final File file = new File(context.getFilesDir(), filename);
            if (file.exists()) {
                final FileInputStream fis = new FileInputStream(file);
                final ObjectInputStream is = new ObjectInputStream(fis);
                result = is.readObject();
                is.close();
                fis.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Fehler beim Einlesen", e);
        }
        return result;
    }

    // Caching
    // ---------------------------------------------------------------------------------------------

    private boolean cacheStillValid(HofObject hofObject, final int cacheTime) {
        final Date today = new Date();
        Date lastCached = new Date();

        if (hofObject.getLastSaved() != null) {
            lastCached = hofObject.getLastSaved();

            Calendar cal = Calendar.getInstance();
            cal.setTime(lastCached);
            cal.add(Calendar.MINUTE, cacheTime);
            lastCached = cal.getTime();
        }

        return lastCached.after(today);
    }

    public final void cleanCache(final Context context) {
        dataConnector.cleanCache(context, Define.MAX_CACHE_TIME);
    }

    // FCM
    // ---------------------------------------------------------------------------------------------

    public void registerFCMServer(Context context) {
        if (Define.PUSH_NOTIFICATIONS_ENABLED) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            final boolean registerForChangesNotifications = sharedPreferences.getBoolean("changes_notifications", false);

            if (registerForChangesNotifications) {
                registerFCMServerForce(context);
            }
        }
    }

    public void registerFCMServerForce(Context context) {
        Set<String> ids = new HashSet<>();

        Schedule schedule = this.getSchedule(context);

        if (getMySchedule(context).getIds().size() > 0) {
            ids = getMySchedule(context).getIds();
        } else if (schedule.getLectures().size() > 0) {
            for (LectureItem li : schedule.getLectures()) {
                ids.add(String.valueOf(li.getId()));
            }
        } else {
            return;
        }

        new RegisterLectures().registerLectures(ids);
    }
}
