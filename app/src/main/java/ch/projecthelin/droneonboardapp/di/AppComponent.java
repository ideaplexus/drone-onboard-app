package ch.projecthelin.droneonboardapp.di;

import android.content.Context;
import ch.projecthelin.droneonboardapp.activities.MainActivity;
import ch.projecthelin.droneonboardapp.activities.RegisterDroneActivity;
import ch.projecthelin.droneonboardapp.fragments.DroneFragment;
import ch.projecthelin.droneonboardapp.fragments.OverviewFragment;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {AppModule.class})
public interface AppComponent {
    void inject(RegisterDroneActivity activity);
    void inject(MainActivity activity);
    void inject(OverviewFragment fragment);
    void inject(DroneFragment fragment);
    Context context();
}