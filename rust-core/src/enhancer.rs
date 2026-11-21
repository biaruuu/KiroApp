//! Anime enhancer planning: how strong a picture effect this phone can run without making the
//! video stutter. Pure functions, no JNI, so they are easy to test.
//!
//! Tiers: 0 off, 1 light (one pass), 2 balanced (Anime4K Deblur DoG), 3 quality (DoG + line darken).

/// First run of digits found after `marker` in `text` (case already lowered).
fn number_after(text: &str, marker: &str) -> Option<u32> {
    let start = text.find(marker)? + marker.len();
    let digits: String = text[start..]
        .chars()
        .skip_while(|c| !c.is_ascii_digit())
        .take_while(|c| c.is_ascii_digit())
        .collect();
    digits.parse().ok()
}

/// Rough 0-100 power of a GPU from its renderer string (for example "Adreno (TM) 650").
/// Unknown GPUs score a cautious 30.
pub fn gpu_score(renderer: &str) -> i32 {
    let r = renderer.to_ascii_lowercase();
    if r.contains("adreno") {
        return match number_after(&r, "adreno").unwrap_or(0) {
            0 => 30,
            1..=399 => 10,
            400..=499 => 20,
            500..=509 => 32,
            510..=529 => 38,
            530..=549 => 46,
            600..=609 => 40,
            610..=619 => 42,
            620..=629 => 52,
            630..=639 => 58,
            640..=649 => 70,
            650..=659 => 78,
            660..=699 => 85,
            700..=739 => 88,
            _ => 95,
        };
    }
    if r.contains("immortalis") || r.contains("mali") {
        let n = number_after(&r, "-g")
            .or_else(|| number_after(&r, " g"))
            .unwrap_or(0);
        if n == 0 {
            // Old Mali-T / Mali-4xx parts.
            return if r.contains("mali") { 8 } else { 30 };
        }
        return match n {
            1..=49 => 25,
            50..=59 => 38,
            60..=69 => 55,
            70..=72 => 48,
            73..=76 => 60,
            77 => 70,
            78 => 76,
            610..=699 => 60,
            700..=714 => 76,
            715..=719 => 82,
            720..=799 => 88,
            _ => 92,
        };
    }
    if r.contains("xclipse") {
        return 80;
    }
    if r.contains("powervr") {
        return if r.contains("ge8") || r.contains("rogue") { 20 } else { 45 };
    }
    30
}

/// Strongest tier this hardware can sustain: 0..=3. Low-end phones get 1 (light) when they can,
/// and 0 when they cannot.
pub fn hardware_ceiling(ram_mb: i32, cores: i32, sdk: i32, gpu: i32) -> i32 {
    if sdk < 26 || cores < 4 {
        return 0;
    }
    if gpu >= 72 && ram_mb >= 5500 && cores >= 6 {
        3
    } else if gpu >= 50 && ram_mb >= 3500 {
        2
    } else if gpu >= 24 && ram_mb >= 1800 {
        1
    } else {
        0
    }
}

/// Tier to start with for this video. `user_mode`: 0 auto, 1..=3 a tier the user picked.
/// `thermal` follows Android's PowerManager status (0 none ... 3 severe ... 6 shutdown).
pub fn start_tier(ceiling: i32, video_height: i32, fps10: i32, battery_saver: bool, thermal: i32, user_mode: i32) -> i32 {
    let mut t = if (1..=3).contains(&user_mode) { user_mode } else { ceiling.clamp(0, 3) };
    // The effect runs on every frame of the video, so big or fast videos get less.
    if video_height > 1440 {
        return 0;
    }
    if video_height > 1080 {
        t = t.min(1);
    }
    if fps10 >= 500 && video_height > 720 {
        t = t.min(1);
    }
    if battery_saver {
        t = if user_mode == 0 { 0 } else { t.min(1) };
    }
    if thermal >= 3 {
        return 0;
    }
    if thermal == 2 {
        t = t.min(1);
    }
    t.max(0)
}

/// Called every few seconds while playing. `drop_permille` is dropped frames per 1000 shown in the
/// last window, `calm_secs` how long playback has been smooth since the last change, and
/// `downgrades` how many times this session already had to step down.
/// Steps down at once when frames drop, steps up slowly, and stops trying after two downgrades.
pub fn governor_next(cur: i32, ceiling: i32, drop_permille: i32, calm_secs: i32, downgrades: i32) -> i32 {
    let ceiling = ceiling.clamp(0, 3);
    if cur > ceiling {
        return ceiling;
    }
    if drop_permille >= 250 {
        return (cur - 2).max(0);
    }
    if drop_permille >= 40 {
        return (cur - 1).max(0);
    }
    if drop_permille <= 5 && calm_secs >= 45 && cur < ceiling && downgrades < 2 {
        return cur + 1;
    }
    cur
}

/// How much to enlarge the picture before sharpening, times 10 (10 = none, 15, 20).
/// Only balanced and quality tiers upscale, and only when the video is clearly smaller than the
/// screen. `screen_w` is the long side of the display, `video_w` the decoded width.
pub fn upscale_factor10(tier: i32, video_w: i32, screen_w: i32) -> i32 {
    if tier < 2 || video_w <= 0 || screen_w <= 0 {
        return 10;
    }
    // Wanted enlargement, times 100.
    let want = screen_w as i64 * 100 / video_w as i64;
    let mut f = if tier >= 3 && want >= 180 {
        20
    } else if want >= 130 {
        15
    } else {
        10
    };
    // Never produce more than 2560 px wide: that costs more than it shows.
    while f > 10 && video_w as i64 * f as i64 / 10 > 2560 {
        f -= 5;
    }
    f
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scores_known_gpus() {
        assert!(gpu_score("Adreno (TM) 650") >= 70);
        assert!(gpu_score("Adreno (TM) 306") < 20);
        assert!(gpu_score("Mali-G52 MC2") < 50);
        assert!(gpu_score("Mali-G78 MP14") >= 70);
        assert!(gpu_score("Mali-T720") < 20);
        assert!(gpu_score("Immortalis-G715") >= 80);
        assert_eq!(gpu_score(""), 30);
    }

    #[test]
    fn ceiling_scales_with_hardware() {
        assert_eq!(hardware_ceiling(8000, 8, 34, 85), 3);
        assert_eq!(hardware_ceiling(4000, 8, 33, 55), 2);
        assert_eq!(hardware_ceiling(2000, 8, 30, 38), 1);
        assert_eq!(hardware_ceiling(1000, 4, 28, 38), 0);
        assert_eq!(hardware_ceiling(8000, 8, 24, 85), 0);
        assert_eq!(hardware_ceiling(3000, 2, 30, 60), 0);
    }

    #[test]
    fn big_hot_or_saving_means_less() {
        assert_eq!(start_tier(3, 720, 240, false, 0, 0), 3);
        assert_eq!(start_tier(3, 1080, 240, false, 0, 0), 3);
        assert_eq!(start_tier(3, 1440, 240, false, 0, 0), 1);
        assert_eq!(start_tier(3, 2160, 240, false, 0, 0), 0);
        assert_eq!(start_tier(3, 1080, 600, false, 0, 0), 1);
        assert_eq!(start_tier(3, 720, 240, true, 0, 0), 0);
        assert_eq!(start_tier(3, 720, 240, false, 3, 0), 0);
        assert_eq!(start_tier(3, 720, 240, false, 2, 0), 1);
        assert_eq!(start_tier(1, 720, 240, false, 0, 3), 3);
    }

    #[test]
    fn upscale_only_when_it_helps() {
        assert_eq!(upscale_factor10(1, 640, 2400), 10);
        assert_eq!(upscale_factor10(2, 1920, 2400), 10);
        assert_eq!(upscale_factor10(2, 1280, 2400), 15);
        assert_eq!(upscale_factor10(2, 854, 2400), 15);
        assert_eq!(upscale_factor10(3, 854, 2400), 20);
        assert_eq!(upscale_factor10(3, 1280, 2400), 20);
        assert_eq!(upscale_factor10(3, 1300, 2400), 15);
        assert_eq!(upscale_factor10(3, 1400, 3000), 15);
        assert_eq!(upscale_factor10(3, 0, 2400), 10);
    }

    #[test]
    fn governor_drops_fast_and_climbs_slowly() {
        assert_eq!(governor_next(3, 3, 60, 0, 0), 2);
        assert_eq!(governor_next(3, 3, 300, 0, 0), 1);
        assert_eq!(governor_next(1, 3, 100, 0, 0), 0);
        assert_eq!(governor_next(1, 3, 0, 30, 0), 1);
        assert_eq!(governor_next(1, 3, 0, 50, 0), 2);
        assert_eq!(governor_next(1, 3, 0, 50, 2), 1);
        assert_eq!(governor_next(3, 2, 0, 0, 0), 2);
        assert_eq!(governor_next(2, 3, 20, 100, 0), 2);
    }
}
