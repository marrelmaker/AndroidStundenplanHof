package de.hof.university.app.onboarding.Fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;

import java.util.ArrayList;
import java.util.HashMap;

import de.hof.university.app.MainActivity;
import de.hof.university.app.R;
import de.hof.university.app.data.SettingsController;
import de.hof.university.app.data.SettingsKeys;
import de.hof.university.app.data.TaskComplete;
import de.hof.university.app.model.settings.StudyCourse;

/**
 * Created by patrickniepel on 03.01.18.
 */

public class OnboardingStudyFragment extends Fragment implements TaskComplete {

    private Button studyTermBtn, degreeProgramBtn, semesterBtn, continueBtn;
    private SettingsController settingsCtrl;

    //ArrayAdapter for dialogs
    private ArrayList<String> termList, degreeProgramList, degreeProgramListTags, semesterList;
    private String selectedTerm, selectedDegreeProgram, selectedSemester;

    //Keys for saving settings
    private ArrayList<String> termShort, degreeProgramShort, semesterShort;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        termList = new ArrayList<>();
        degreeProgramList = new ArrayList<>();
        degreeProgramListTags = new ArrayList<>();
        semesterList = new ArrayList<>();
        selectedTerm = selectedDegreeProgram = selectedSemester = "";

        settingsCtrl = new SettingsController(getActivity(), this);

        //keys
        termShort = new ArrayList<>();
        degreeProgramShort = new ArrayList<>();
        semesterShort = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_onboarding_study, container, false);
        return v;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupLayout();
        setupClickListener();
        fillLayoutIfPossible();
    }

    @Override
    public void onResume() {
        super.onResume();
        final MainActivity mainActivity = (MainActivity) getActivity();
        mainActivity.getSupportActionBar().setTitle(R.string.onboarding_study);
    }

    private void setupLayout() {
        studyTermBtn = getActivity().findViewById(R.id.onboarding_study_study_term_button);
        degreeProgramBtn = getActivity().findViewById(R.id.onboarding_study_degree_program_button);
        semesterBtn = getActivity().findViewById(R.id.onboarding_study_semester_button);
        continueBtn = getActivity().findViewById(R.id.onboarding_study_continue_button);

        degreeProgramBtn.setEnabled(false);
        degreeProgramBtn.setTextColor(Color.GRAY);
        semesterBtn.setEnabled(false);
        semesterBtn.setTextColor(Color.GRAY);

        fillTermList();
    }

    private void setupClickListener() {

        studyTermBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetButtons();

                createDialog("term");
            }
        });

        degreeProgramBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                downloadDegreeProgramList();
            }
        });

        semesterBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createDialog("semester");
            }
        });

        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //Everything must be selected to continue
                if (selectedTerm.isEmpty() || selectedDegreeProgram.isEmpty() || selectedSemester.isEmpty()) {
                    new AlertDialog.Builder(getView().getContext())
                            .setTitle(R.string.onboarding_error_text)
                            .setMessage(R.string.onboarding_error_not_selected_message_study)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //nothing to do here. Just close the message
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }
                else {
                    startOnboardingMenuPlan();
                }
            }
        });
    }

    private void fillLayoutIfPossible() {
        if (!selectedTerm.isEmpty()) {
            studyTermBtn.setText(selectedTerm);
            degreeProgramBtn.setEnabled(true);
        }
        if (!selectedDegreeProgram.isEmpty()) {
            degreeProgramBtn.setText(selectedDegreeProgram);
            semesterBtn.setEnabled(true);
        }
        if (!selectedSemester.isEmpty()) {
            semesterBtn.setText(selectedSemester);
        }
    }

    @Override
    public void onTaskComplete(HashMap<String, CharSequence[]> data) {
        CharSequence[] entries = data.get("entries");
        CharSequence[] entryValues = data.get("entryValues");

        if (entries != null) {
            if (entries.length > 0) {

                degreeProgramBtn.setEnabled(true);
                degreeProgramList.clear();
                degreeProgramListTags.clear();
                fillDegreeProgramList(entries, entryValues);
                createDialog("degreeProgram");
            }
        }
    }

    private void fillDegreeProgramList(CharSequence[] entries, CharSequence[] entryValues) {

        for(CharSequence entry : entries) {
            degreeProgramList.add((String) entry);
        }

        for(CharSequence value : entryValues) {
            degreeProgramListTags.add((String) value);
        }
    }

    private void startOnboardingMenuPlan() {
        termList.clear();
        termShort.clear();

        FragmentManager manager = getFragmentManager();
        FragmentTransaction trans = manager.beginTransaction();
        trans.addToBackStack(OnboardingMenuPlanFragment.class.getName());
        trans.replace(R.id.content_main, new OnboardingMenuPlanFragment());
        trans.commit();
    }

    private void fillTermList() {
        String[] termArray = MainActivity.getAppContext().getResources().getStringArray(R.array.term_time);
        for (String t : termArray) {
            termList.add(t);
        }

        String[] termShortArray = MainActivity.getAppContext().getResources().getStringArray(R.array.term_time_values);
        for (String t : termShortArray) {
            termShort.add(t);
        }
    }

    private void updateSemesterData(String selectedTag) {
        if ( (settingsCtrl.getStudyCourseList() == null) || selectedDegreeProgram.isEmpty() ) {
            //Leave list empty
            return;
        }

        for ( final StudyCourse studyCourse : settingsCtrl.getStudyCourseList() ) {

            if ( studyCourse.getTag().equals(selectedTag) ) {

                final ArrayList<String> entryValues = new ArrayList<>();
                semesterList.clear();
                for ( int j = 0; j < studyCourse.getTerms().size(); ++j ) {
                    semesterList.add(studyCourse.getTerms().get(j));
                }

                if ( semesterList != null ) {
                    if ( semesterList.size() > 0 ) {
                        semesterBtn.setEnabled(true);
                        semesterList.addAll(entryValues);
                    } else {
                        semesterBtn.setEnabled(false);
                    }
                }
            }
        }
    }

    private void createDialog(final String valueKey) {

        AlertDialog.Builder builderSingle = new AlertDialog.Builder(getActivity());

        final ArrayAdapter<String> valueAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.select_dialog_singlechoice);

        if(valueKey.equals("term")) {
            valueAdapter.addAll(termList);
        }
        if(valueKey.equals("degreeProgram")) {
            valueAdapter.addAll(degreeProgramList);
        }
        if(valueKey.equals("semester")) {
            valueAdapter.addAll(semesterList);
        }

        builderSingle.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builderSingle.setAdapter(valueAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                if(valueKey.equals("term")) {
                    selectedTerm = valueAdapter.getItem(which);
                    studyTermBtn.setText(selectedTerm);
                    Log.d("+++++++++++++++++++++++", termShort.get(which));
                    settingsCtrl.saveStringSettings(SettingsKeys.TERM, termShort.get(which));
                    degreeProgramBtn.setEnabled(true);
                    degreeProgramBtn.setTextColor(Color.BLACK);
                }
                if(valueKey.equals("degreeProgram")) {
                    selectedDegreeProgram = valueAdapter.getItem(which);
                    String selectedDegreeProgramList = degreeProgramListTags.get(which);
                    degreeProgramBtn.setText(selectedDegreeProgram);
                    settingsCtrl.saveStringSettings(SettingsKeys.DEGREE_PROGRAM, degreeProgramListTags.get(which));
                    semesterBtn.setEnabled(true);
                    semesterBtn.setTextColor(Color.BLACK);
                    updateSemesterData(selectedDegreeProgramList);
                }
                if(valueKey.equals("semester")) {
                    selectedSemester = valueAdapter.getItem(which);
                    settingsCtrl.saveStringSettings(SettingsKeys.SEMESTER, semesterList.get(which));
                    semesterBtn.setText(selectedSemester);
                }
            }
        });
        builderSingle.show();
    }

    private void downloadDegreeProgramList() {
        String termValue = "";

        termValue = selectedTerm.toLowerCase().startsWith("w") ? "WS" : "SS";

        final String[] params = new String[2];
        params[0] = termValue;
        params[1] = String.valueOf(false);
        settingsCtrl.executeSemesterTask(this, params);
    }

    private void resetButtons() {
        degreeProgramBtn.setText(R.string.studiengang);
        degreeProgramBtn.setEnabled(false);

        semesterBtn.setText(R.string.semester);
        semesterBtn.setEnabled(false);
    }
}
