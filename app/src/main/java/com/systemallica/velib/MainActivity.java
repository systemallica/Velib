package com.systemallica.velib;

import android.app.ActivityManager;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import com.example.android.trivialdrivesample.util.IabException;
import com.example.android.trivialdrivesample.util.IabHelper;
import com.example.android.trivialdrivesample.util.IabResult;
import com.example.android.trivialdrivesample.util.Purchase;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.systemallica.velib.Fragments.AboutFragment;
import com.systemallica.velib.Fragments.DonateFragment;
import com.systemallica.velib.Fragments.MainFragment;
import com.systemallica.velib.Fragments.SettingsFragment;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static android.view.View.GONE;
import static com.systemallica.velib.MyContextWrapper.getSystemLocale;
import static com.systemallica.velib.MyContextWrapper.getSystemLocaleLegacy;
import static com.systemallica.velib.R.layout.activity_main;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, IabHelper.OnIabSetupFinishedListener, IabHelper.OnIabPurchaseFinishedListener {

    NavigationView navigationView;
    FragmentManager mFragmentManager;
    public static final String PREFS_NAME = "MyPrefsFile";
    private IabHelper billingHelper;
    Context context = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

        boolean navBar = settings.getBoolean("navBar", true);

        int colorPrimary = ContextCompat.getColor(context, R.color.colorPrimary);

        //Apply preferences navBar preference
        if(navBar && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            getWindow().setNavigationBarColor(colorPrimary);
        }

        //Recents implementation
        //String title = null;  // You can either set the title to whatever you want or just use null and it will default to your app/activity name
        Bitmap recentsIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.splash_inverted);//Choose the icon

        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityManager.TaskDescription description = new ActivityManager.TaskDescription(null, recentsIcon, colorPrimary);
            this.setTaskDescription(description);
        }

        //set view to main
        setContentView(activity_main);

        //init toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //init drawer
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        //init navigation view
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //Inflate main fragment
        mFragmentManager = getFragmentManager();
        FragmentTransaction ft = mFragmentManager.beginTransaction();

        if (savedInstanceState == null) {
            ft.replace(R.id.containerView, new MainFragment(), "mainFragment").commit();
            navigationView.getMenu().getItem(0).setChecked(true);
        }

        //Check internet
        final ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if(cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            final boolean isConnected = activeNetwork != null &&
                    activeNetwork.isConnected();

            //React to the check
            if (!isConnected) {
                //Prompt an alert dialog to the user
                new AlertDialog.Builder(context)
                        .setTitle(R.string.no_internet)
                        .setMessage(R.string.no_internet_message)
                        .setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                System.exit(0);
                            }
                        })

                        .setNegativeButton(R.string.continuer, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                //Do nothing
                            }
                        })

                        .setIcon(R.drawable.ic_report_problem_black_24dp)
                        .show();
            } else {
                getLatestVersion();
            }
        }

        boolean donationPurchased = settings.getBoolean("donationPurchased", false);

        //Ads management
        AdView mAdView = findViewById(R.id.adView);
        if(!donationPurchased) {
            //Ad request and load
            AdRequest adRequest = new AdRequest.Builder().build();
            mAdView.loadAd(adRequest);
        }

        boolean firstLaunch = settings.getBoolean("firstLaunch", true);
        //License management
        if (firstLaunch) {
            String clave = PrivateInfo.clave;
            billingHelper = new IabHelper(context, clave);
            billingHelper.startSetup(this);
        }

    }

    public void startBuyProcess(){
        String clave = PrivateInfo.clave;
        billingHelper = new IabHelper(context, clave);
        billingHelper.startSetup(this);
    }

    @Override
    public void onIabSetupFinished(IabResult result) {
        if (result.isSuccess()) {

            try{
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                boolean firstLaunch = settings.getBoolean("firstLaunch", true);
                if(firstLaunch){
                    SharedPreferences.Editor editor = settings.edit();
                    if(billingHelper.queryInventory(true, null).hasPurchase(PrivateInfo.donation)) {
                        //How-to consume purchase if already made
                        //Inventory a = billingHelper.queryInventory(true, null);
                        //Purchase item = a.getPurchase(Donation.donation);
                        //billingHelper.consumeAsync(item, null);

                        editor.putBoolean("donationPurchased", true);
                        editor.apply();

                        Snackbar.make(this.findViewById(android.R.id.content), R.string.license, Snackbar.LENGTH_LONG).show();
                    }
                    editor.putBoolean("firstLaunch", false);
                    editor.apply();
                    if (billingHelper != null) {
                        billingHelper.dispose();
                    }
                    billingHelper = null;
                }else {
                    if (!billingHelper.queryInventory(true, null).hasPurchase(PrivateInfo.donation)) {
                        compraElemento();
                    }
                }

            } catch(IabException e){
                e.printStackTrace();
            }

        } else {

            errorAlIniciar();
        }

    }


    protected void errorAlIniciar() {
        Snackbar.make(this.findViewById(android.R.id.content), R.string.purchase_failed, Snackbar.LENGTH_SHORT).show();
    }


    protected void compraElemento() {
        purchaseItem(PrivateInfo.donation);
    }


    protected void purchaseItem(String sku) {
        billingHelper.launchPurchaseFlow(this, sku, 123, this);
    }


    @Override
    public void onIabPurchaseFinished(IabResult result, Purchase info) {
        if (result.isFailure()) {
            compraFallida();
        } else {
            compraCorrecta(result, info);
        }

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Pass on the activity result to the helper for handling
        if (!billingHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    protected void compraCorrecta(IabResult result, Purchase info){

        Log.e("Purchase result", result.toString());
        Log.e("Purchase info", info.toString());

        // Consumimos los elementos a fin de poder probar varias compras
        //billingHelper.consumeAsync(info, null);
        Snackbar.make(this.findViewById(android.R.id.content), R.string.purchase_success, Snackbar.LENGTH_SHORT).show();

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("donationPurchased", true);
        editor.apply();

        AdView mAdView =  findViewById(R.id.adView);
        if(mAdView!=null) {
            mAdView.setVisibility(GONE);
            mAdView.destroy();
        }
        if (billingHelper != null) {
            billingHelper.dispose();
        }
        billingHelper = null;
    }

    protected void compraFallida(){
        Snackbar.make(this.findViewById(android.R.id.content), R.string.purchase_failed, Snackbar.LENGTH_SHORT).show();
        if (billingHelper != null) {
            billingHelper.dispose();
        }
        billingHelper = null;
    }

    @Override
    protected void attachBaseContext(Context newBase) {

        //Changing language
        final SharedPreferences settings = newBase.getSharedPreferences(PREFS_NAME, 0);
        String locale = settings.getString("locale", "default_locale");

        //Get default system locale
        Configuration config = newBase.getResources().getConfiguration();
        Locale sysLocale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sysLocale = getSystemLocale(config);
        } else {
            sysLocale = getSystemLocaleLegacy(config);
        }

        //Apply it if user didn't specify a locale
        if (locale.equals("default_locale")){
            super.attachBaseContext(MyContextWrapper.wrap(newBase,sysLocale.getLanguage()));
            //Else apply user choice
        }else{
            super.attachBaseContext(MyContextWrapper.wrap(newBase,locale));
        }
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        AdView mAdView = findViewById(R.id.adView);

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        boolean removedAds = settings.getBoolean("removedAds", false);

        if (id == R.id.nav_map) {

            if(!removedAds) {
                mAdView.setVisibility(View.VISIBLE);
            }

            //Change toolbar title
            this.setTitle(R.string.nav_map);
            SharedPreferences.Editor editor = settings.edit();
            boolean isChanged = settings.getBoolean("isChanged", false);

            FragmentTransaction ft = mFragmentManager.beginTransaction();
            if(isChanged){
                ft.replace(R.id.containerView, new MainFragment(), "mainFragment");
                editor.putBoolean("isChanged", false);
                editor.apply();
            }
            else {
                ft.show(getFragmentManager().findFragmentByTag("mainFragment"));
            }
            if(getFragmentManager().findFragmentByTag("aboutFragment")!=null) {
                ft.remove(getFragmentManager().findFragmentByTag("aboutFragment"));
            }
            if(getFragmentManager().findFragmentByTag("settingsFragment")!=null){
                ft.remove(getFragmentManager().findFragmentByTag("settingsFragment"));
            }
            if(getFragmentManager().findFragmentByTag("donateFragment")!=null){
                ft.remove(getFragmentManager().findFragmentByTag("donateFragment"));
            }
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.commit();

        } else if (id == R.id.nav_settings) {

            mAdView.setVisibility(View.GONE);

            FragmentTransaction ft = mFragmentManager.beginTransaction();
            ft.hide(getFragmentManager().findFragmentByTag("mainFragment"));
            if(getFragmentManager().findFragmentByTag("aboutFragment")!=null) {
                ft.remove(getFragmentManager().findFragmentByTag("aboutFragment"));
            }
            if(getFragmentManager().findFragmentByTag("donateFragment")!=null){
                ft.remove(getFragmentManager().findFragmentByTag("donateFragment"));
            }
            if(getFragmentManager().findFragmentByTag("settingsFragment")!=null){
                ft.remove(getFragmentManager().findFragmentByTag("settingsFragment"));
            }
            ft.add(R.id.containerView, new SettingsFragment(), "settingsFragment");
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.commit();

        } else if (id == R.id.nav_donate) {

            if(!removedAds) {
                mAdView.setVisibility(View.VISIBLE);
            }

            FragmentTransaction ft = mFragmentManager.beginTransaction();
            ft.hide(getFragmentManager().findFragmentByTag("mainFragment"));
            if(getFragmentManager().findFragmentByTag("settingsFragment")!=null){
                ft.remove(getFragmentManager().findFragmentByTag("settingsFragment"));
            }
            if(getFragmentManager().findFragmentByTag("aboutFragment")!=null){
                ft.remove(getFragmentManager().findFragmentByTag("aboutFragment"));
            }
            if(getFragmentManager().findFragmentByTag("donateFragment")!=null){
                ft.remove(getFragmentManager().findFragmentByTag("donateFragment"));
            }
            ft.add(R.id.containerView, new DonateFragment(), "donateFragment");
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.commit();

        } else if (id == R.id.nav_share) {

            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                String sAux = "https://play.google.com/store/apps/details?id=com.systemallica.velib";
                i.putExtra(Intent.EXTRA_TEXT, sAux);
                startActivity(i);
            } catch(Exception e) {
                //e.toString();
            }

        } else if (id == R.id.nav_about) {

            if(!removedAds) {
                mAdView.setVisibility(View.VISIBLE);
            }

            FragmentTransaction ft = mFragmentManager.beginTransaction();
            ft.hide(getFragmentManager().findFragmentByTag("mainFragment"));
            if(getFragmentManager().findFragmentByTag("settingsFragment")!=null){
                ft.remove(getFragmentManager().findFragmentByTag("settingsFragment"));
            }
            if(getFragmentManager().findFragmentByTag("donateFragment")!=null){
                ft.remove(getFragmentManager().findFragmentByTag("donateFragment"));
            }
            if(getFragmentManager().findFragmentByTag("aboutFragment")!=null){
                ft.remove(getFragmentManager().findFragmentByTag("aboutFragment"));
            }
            ft.add(R.id.containerView, new AboutFragment(), "aboutFragment");
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.commit();

        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void getLatestVersion(){
        final OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://raw.githubusercontent.com/systemallica/Velib/master/VersionCode")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    ResponseBody responseBody = response.body();
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    String latestVersionTemp = "";

                    if(responseBody!=null) {
                        latestVersionTemp = responseBody.string();
                    }

                    String latestVersion = latestVersionTemp;
                    checkUpdate(latestVersion.trim());

                }finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
    }

    public void checkUpdate(final String latestVersion){

        int versionCode = BuildConfig.VERSION_CODE;
        int versionGit = Integer.parseInt(latestVersion);

        if (versionCode < versionGit) {

            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            boolean noUpdate = settings.getBoolean("noUpdate", false);

            if (!noUpdate) {
                runOnUiThread(new Runnable() {
                    public void run() {

                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle(R.string.update_available)
                                .setMessage(R.string.update_message)
                                .setIcon(R.drawable.ic_system_update_black_24dp)
                                .setPositiveButton(R.string.update_ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.systemallica.velib"));
                                        startActivity(browserIntent);
                                    }
                                })
                                .setNegativeButton(R.string.update_not_now, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Do nothing
                                    }
                                })
                                .setNeutralButton(R.string.update_never, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        final SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                                        SharedPreferences.Editor editor = settings.edit();
                                        editor.putBoolean("noUpdate", true);
                                        editor.apply();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();

                    }
                });
            }
        }else if(versionCode > versionGit){
            runOnUiThread(new Runnable() {
                public void run() {

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(R.string.alpha_title)
                            .setMessage(R.string.alpha_message)
                            //.setIcon(R.drawable.ic_system_update_black_24dp)
                            .setPositiveButton(R.string.update_ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // Do nothing
                                }
                            });
                    AlertDialog dialog = builder.create();
                    dialog.show();

                }
            });
        }
    }

    @Override
    public void onDestroy() {

        super.onDestroy();
    }
}
