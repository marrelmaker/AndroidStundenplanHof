/*
 * Copyright (c) 2018 Hochschule Hof
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

package de.hof.university.app.fragment;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import de.hof.university.app.MainActivity;
import de.hof.university.app.R;
import de.hof.university.app.calendar.CalendarSynchronization;

/**
 * Created by Daniel on 22.11.2017.
 */

public class SettingsCalendarSynchronizationFragment extends PreferenceFragmentCompat {

	public final static String TAG = "SettingCalendarSyncFrag";

	private final int REQUEST_CODE_CALENDAR_TURN_ON_PERMISSION =  2;
	private final int REQUEST_CODE_CALENDAR_TURN_OFF_PERMISSION =  3;

	private CalendarSynchronization calendarSynchronization = null;

	private SharedPreferences sharedPreferences;

	@Override
	public void onCreate( @Nullable Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );

		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.getAppContext());

		this.calendarSynchronization = CalendarSynchronization.getInstance();

		// Load the Calendar preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences_calendar_synchronization);

		// Calendar synchronization, switch on or off, in general
		final CheckBoxPreference calendar_synchronization = (CheckBoxPreference) findPreference(getString(R.string.PREF_KEY_CALENDAR_SYNCHRONIZATION));
		calendar_synchronization.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {

				// TODO switch
				if ( (Boolean) newValue ) {
					// an schalten
					turnCalendarSyncOn();
				} else {

					// check, if accidentally switsched off.

					// aus schalten
					turnCalendarSyncOff();
				}
				return true;
			}
		});


		final EditTextPreference calendarReminderEditText = (EditTextPreference) findPreference(getString(R.string.PREF_KEY_CALENDAR_REMINDER));

		calendarReminderEditText.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (newValue instanceof String) {
					updateSummary((String) newValue);
					calendarSynchronization.updateCalendar();
					return true;
				} else {
					return false;
				}
			}
		});

	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

	}

	@Override
	public void onResume() {
		super.onResume();

		final MainActivity mainActivity = (MainActivity) getActivity();
		mainActivity.getSupportActionBar().setTitle(Html.fromHtml("<font color='"+ ContextCompat.getColor(MainActivity.getAppContext(), R.color.colorBlack)+"'>"+ getString(R.string.calendar_synchronization)+"</font>"));
		mainActivity.getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_menu_accent_24dp);
		
		final NavigationView navigationView = mainActivity.findViewById(R.id.nav_view);
		navigationView.getMenu().findItem(R.id.nav_einstellungen).setChecked(true);


		final CheckBoxPreference calendar_synchronization = (CheckBoxPreference) findPreference(getString( R.string.PREF_KEY_CALENDAR_SYNCHRONIZATION));
		final EditTextPreference calendarReminderEditText = (EditTextPreference) findPreference(getString(R.string.PREF_KEY_CALENDAR_REMINDER));

		// aktiviere oder deaktiviere die Kalender Synchronisation je nachdem ob die experimentellen Funktionen aktiviert sind oder nicht
		boolean experimentalFeaturesEnabled = sharedPreferences.getBoolean(getString(R.string.PREF_KEY_EXPERIMENTAL_FEATURES_ENABLED), false);

		calendar_synchronization.setEnabled(experimentalFeaturesEnabled);
		calendarReminderEditText.setEnabled(experimentalFeaturesEnabled);

		// update summary
		updateSummary(null);
	}

	private void updateSummary(String newValue) {
		final Preference calendarReminderPref = findPreference(getString(R.string.PREF_KEY_CALENDAR_REMINDER));
		int minutes;
		try {
			if (newValue == null) {
				minutes = Integer.parseInt(sharedPreferences.getString(getString(R.string.PREF_KEY_CALENDAR_REMINDER), "" + getResources().getInteger(R.integer.CALENDAR_REMINDER_DEFAULT_VALUE)));
			} else {
				minutes = Integer.parseInt(newValue);
			}
		} catch (NumberFormatException e) {
			minutes = getResources().getInteger(R.integer.CALENDAR_REMINDER_DEFAULT_VALUE);
			Log.e(TAG, "reminderMinutes was set to default value", e);
		}

		if (minutes == 1) {
			// Einzahl
			calendarReminderPref.setSummary("" + minutes + " " + getString(R.string.minute));
		} else {
			// Mehrzahl
			calendarReminderPref.setSummary("" + minutes + " " + getString(R.string.minutes));
		}
	}

	private void requestCalendarPermission( int requestCode) {

		// From MARSHMELLOW (OS 6) on
		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
			this.requestPermissions(
					new String[]{Manifest.permission.READ_CALENDAR,
							Manifest.permission.WRITE_CALENDAR},
					requestCode);
		}
	}

	@Override
	public void onRequestPermissionsResult( int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch (requestCode) {
			case REQUEST_CODE_CALENDAR_TURN_ON_PERMISSION:
				if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					// Permission granted
					turnCalendarSyncOn();
				} else {
					// Permission Denied
					Toast.makeText(getActivity(), R.string.calendar_synchronization_permissionNotGranted, Toast.LENGTH_SHORT)
							.show();
					// Calendar Sync aus schalten
					((CheckBoxPreference) findPreference(getString(R.string.PREF_KEY_CALENDAR_SYNCHRONIZATION))).setChecked(false);
				}
				break;
			case REQUEST_CODE_CALENDAR_TURN_OFF_PERMISSION:
				if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					// Permission granted
					calendarSynchronization.stopCalendarSynchronization();
				} else {
					// Permission Denied
					Toast.makeText(getActivity(), R.string.calendar_synchronization_permissionNotGranted, Toast.LENGTH_SHORT)
							.show();
					// Calendar Sync ein schalten
					((CheckBoxPreference) findPreference(getString(R.string.PREF_KEY_CALENDAR_SYNCHRONIZATION))).setChecked(true);
				}
			default:
				super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	private void turnCalendarSyncOn() {
		// check for permission
		// wenn keine Berechtigung dann requeste sie und falls erfolgreich komme hier her zurück
		if ((ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
				|| (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)) {
			// keine Berechtigung
			requestCalendarPermission(REQUEST_CODE_CALENDAR_TURN_ON_PERMISSION);
			return;
		}

		final ArrayList<String> calendars = new ArrayList<>();

		// Den localen Kalender als erstes
		calendars.add(getString(R.string.calendar_synchronitation_ownLocalCalendar));

		// Die weiteren Kalender danach
		calendars.addAll(calendarSynchronization.getCalendarsNames());

		final AlertDialog d = new AlertDialog.Builder(getActivity())
				.setTitle(R.string.calendar_synchronization)
				.setMessage(R.string.calendar_synchronization_infoText)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						new AlertDialog.Builder(getActivity())
								.setTitle(R.string.calendar_synchronization_chooseCalendar)
								.setItems(calendars.toArray(new String[calendars.size()]), new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										String calendarName = calendars.get(which);
										if (calendarName.equals(getString(R.string.calendar_synchronitation_ownLocalCalendar))) {
											// lokaler Kalender
											calendarSynchronization.setCalendar(null);
										} else {
											calendarSynchronization.setCalendar(calendarName);
										}
										calendarSynchronization.createAllEvents();
									}
								})
								.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										// Kalender Synchronisation ausschalten
										((CheckBoxPreference) findPreference(getString(R.string.PREF_KEY_CALENDAR_SYNCHRONIZATION))).setChecked(false);
									}
								})
								.setOnCancelListener(new DialogInterface.OnCancelListener() {
									@Override
									public void onCancel(DialogInterface dialog) {
										// Kalender Synchronisation ausschalten
										((CheckBoxPreference) findPreference(getString(R.string.PREF_KEY_CALENDAR_SYNCHRONIZATION))).setChecked(false);
									}
								})
								.setIcon(android.R.drawable.ic_dialog_alert)
								.show();
					}
				})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						// Kalender Synchronisation ausschalten
						((CheckBoxPreference) findPreference(getString(R.string.PREF_KEY_CALENDAR_SYNCHRONIZATION))).setChecked(false);
					}
				})
				.setIcon(android.R.drawable.ic_dialog_alert)
				.create();
		d.show();

		// Make the textview clickable. Must be called after show()
		((TextView)d.findViewById(android.R.id.message)).setMovementMethod( LinkMovementMethod.getInstance());
	}

	private void turnCalendarSyncOff() {
		// mit einem Dialog nachfragen ob der Nutzer die Kalendereinträge behalten möchte
		final AlertDialog d = new AlertDialog.Builder(getActivity())
				.setTitle(R.string.calendar_syncronization_keep_events_title)
				.setMessage(R.string.calendar_syncronization_keep_events_message)
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// behalten, mache nichts
					}
				})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// löschen
						if (( ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
								|| (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)) {
							// keine Berechtigung, hole erst Berechtigung
							requestCalendarPermission(REQUEST_CODE_CALENDAR_TURN_OFF_PERMISSION);
						} else {
							// lösche die Kalendereinträge oder den lokalen Kalender
							calendarSynchronization.stopCalendarSynchronization();
						}
					}
				})
				.setCancelable(false)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.create();
		d.show();
	}
}
