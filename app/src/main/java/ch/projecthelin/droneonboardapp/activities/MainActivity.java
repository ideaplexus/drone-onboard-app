package ch.projecthelin.droneonboardapp.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import ch.helin.messages.converter.JsonBasedMessageConverter;
import ch.helin.messages.dto.DroneInfoDto;
import ch.helin.messages.dto.MissionDto;
import ch.helin.messages.dto.OrderProductDto;
import ch.helin.messages.dto.message.DroneInfoMessage;
import ch.helin.messages.dto.message.missionMessage.*;
import ch.helin.messages.dto.way.Position;
import ch.projecthelin.droneonboardapp.DroneOnboardApp;
import ch.projecthelin.droneonboardapp.MessageReceiver;
import ch.projecthelin.droneonboardapp.R;
import ch.projecthelin.droneonboardapp.fragments.DroneFragment;
import ch.projecthelin.droneonboardapp.fragments.OverviewFragment;
import ch.projecthelin.droneonboardapp.fragments.ServerFragment;
import ch.projecthelin.droneonboardapp.services.DroneConnectionService;
import ch.projecthelin.droneonboardapp.services.LocationService;
import ch.projecthelin.droneonboardapp.services.MessagingConnectionService;
import ch.projecthelin.droneonboardapp.services.MissionListener;
import com.google.android.gms.location.LocationListener;

import javax.inject.Inject;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements LocationListener, MessageReceiver, MissionListener {


    private static final int CARGO_LOAD_REQUEST_CODE = 543;
    public static final String CHANNEL_KEY = "channel";
    public static final String OPEN_PWM_KEY = "open_pwm";
    public static final String CLOSED_PWM_KEY = "closed_pwm";

    private SectionsPagerAdapter mSectionsPagerAdapter;

    private ViewPager mViewPager;

    @Inject
    public MessagingConnectionService messagingConnectionService;

    @Inject
    public DroneConnectionService droneConnectionService;

    @Inject
    public LocationService locationService;

    private JsonBasedMessageConverter jsonBasedMessageConverter = new JsonBasedMessageConverter();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((DroneOnboardApp) getApplication()).component().inject(this);

        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        messagingConnectionService.setDroneToken(loadDroneTokenFromSharedPreferences());
        messagingConnectionService.addMessageReceiver(this);

        locationService.startLocationListening(this, this);

        droneConnectionService.setMissionListener(this);

        droneConnectionService.setServoChannel(loadServoValueFromSharedPreferences(CHANNEL_KEY));
        droneConnectionService.setServoOpenPWM(loadServoValueFromSharedPreferences(OPEN_PWM_KEY));
        droneConnectionService.setServoClosedPWM(loadServoValueFromSharedPreferences(CLOSED_PWM_KEY));
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationService.stopLocationListening();
    }

    @Override
    protected void onResume() {
        super.onResume();
        locationService.startLocationListening(this, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationService.stopLocationListening();
        messagingConnectionService.removeMessageReceiver(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CARGO_LOAD_REQUEST_CODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                startMissionCountDown();
            }
        }
    }

    private void startMissionCountDown() {
        MissionDto currentMission = messagingConnectionService.getCurrentMission();
        droneConnectionService.sendRouteToAutopilot(currentMission.getRoute());

        final AlertDialog dialog = createMissionStartCountDownDialog();

        dialog.show();

        new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                dialog.setMessage("Drone will start in " + (millisUntilFinished / 1000) + " seconds");
            }

            @Override
            public void onFinish() {
                droneConnectionService.startMission();
                dialog.hide();
            }
        }.start();
    }

    private String loadDroneTokenFromSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return sharedPreferences.getString(RegisterDroneActivity.DRONE_TOKEN_KEY, "");
    }

    private int loadServoValueFromSharedPreferences(String key) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return sharedPreferences.getInt(key, 0);
    }

    @Override
    public void onLocationChanged(Location location) {
        sendDroneInfoToServer(location);
    }

    private void sendDroneInfoToServer(Location location) {

        DroneInfoDto droneInfoDto = new DroneInfoDto();
        droneInfoDto.setBatteryState(droneConnectionService.getBatteryState());
        droneInfoDto.setGpsState(droneConnectionService.getGpsState());
        droneInfoDto.setDroneState(droneConnectionService.getDroneState());
        droneInfoDto.setPhonePosition(new Position(location.getLongitude(), location.getLatitude()));
        droneInfoDto.setClientTime(new Date());

        DroneInfoMessage droneInfoMessage = new DroneInfoMessage();
        droneInfoMessage.setDroneInfo(droneInfoDto);

        messagingConnectionService.sendMessage(jsonBasedMessageConverter.parseMessageToString(droneInfoMessage));
    }

    @Override
    public void onAssignMissionMessageReceived(AssignMissionMessage message) {
        final MissionDto mission = message.getMission();
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                showMissionAcceptDialog(mission);
            }
        });
    }

    @Override
    public void onFinalAssignMissionMessageReceived(FinalAssignMissionMessage message) {
        Intent intent = new Intent(this, MissionActivity.class);
        startActivityForResult(intent, CARGO_LOAD_REQUEST_CODE);
        messagingConnectionService.setCurrentMission(message.getMission());
    }

    private void showMissionAcceptDialog(MissionDto mission) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        String missionProductsText = "Do you want to accept this mission? \n";

        OrderProductDto orderProduct = mission.getOrderProduct();

        missionProductsText += orderProduct.getAmount();
        missionProductsText += "   ";
        missionProductsText += orderProduct.getProduct().getName();
        missionProductsText += "\n";

        builder.setMessage(missionProductsText)
                .setTitle("New Mission");

        builder.setPositiveButton("Accept", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                AcceptOrDenyAssignedMission(MissionConfirmType.ACCEPT);
            }
        });

        builder.setNegativeButton("Reject", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                AcceptOrDenyAssignedMission(MissionConfirmType.REJECT);
            }
        });

        builder.create().show();
    }

    private AlertDialog createMissionStartCountDownDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Mission Start");

        builder.setNegativeButton("Abort Start Countdown", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //TODO handle abort of a mission
            }
        });

        return builder.create();
    }

    private void AcceptOrDenyAssignedMission(MissionConfirmType acceptOrReject) {
        ConfirmMissionMessage confirmMissionMessage = new ConfirmMissionMessage();
        confirmMissionMessage.setMissionConfirmType(acceptOrReject);
        messagingConnectionService.sendMessage(jsonBasedMessageConverter.parseMessageToString(confirmMissionMessage));
    }

    @Override
    public void onMissionStarted() {
        Log.d("Mission", "Mission Started");
    }

    @Override
    public void onMissionFinished() {
        Log.d("Mission", "Mission finished");

        FinishedMissionMessage message = new FinishedMissionMessage();
        message.setFinishedType(MissionFinishedType.SUCCESSFUL);
        messagingConnectionService.sendMessage(jsonBasedMessageConverter.parseMessageToString(message));
        messagingConnectionService.setCurrentMission(null);
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0: {
                    return new OverviewFragment();
                }
                case 1: {
                    return new ServerFragment();
                }
                case 2: {
                    return new DroneFragment();
                }
                default: {
                    return new OverviewFragment();
                }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Overview";
                case 1:
                    return "Server";
                case 2:
                    return "Drone";
            }
            return null;
        }
    }
}
