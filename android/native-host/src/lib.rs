use std::path::PathBuf;
use winit::platform::android::activity::AndroidApp;

/// NativeActivity entry point. The app-private game folder is the process working directory;
/// the resource importer places the user-prepared files below it before this activity is launched.
#[unsafe(no_mangle)]
pub fn android_main(app: AndroidApp) {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag("Horizon"),
    );

    let files = app
        .internal_data_path()
        .expect("Android did not provide this app's private files directory");
    let game_dir = PathBuf::from(files).join("game");
    std::fs::create_dir_all(&game_dir).expect("could not create Horizon's private game folder");
    std::env::set_current_dir(&game_dir).expect("could not select Horizon's private game folder");
    horizon_client::entry::set_android_app(app);

    horizon_game::run_main(|| {
        std::hint::black_box(&horizon_game::GAME_IDENTITY);
        horizon_official::register();
        horizon_game::main();
    });
}
