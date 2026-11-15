//! Kiro native core.
//!
//! Small, dependency-light helpers that the Kotlin UI calls through JNI:
//! - `strip_html`   : turns AniList's HTML descriptions into clean plain text
//! - `format_score` : 0-100 AniList score -> "8.7"
//! - `season_for_month` : month number -> AniList season enum string
//! - `format_countdown` : seconds left -> "in 3d 4h 12m 5s" (ticks every second on the details page)
//! - `compact_count` : 1234 -> "1.2K"
//! - `rank_streams` : orders candidate video sources by quality and measured speed
//! - `title_similarity` : 0-100 score of how alike two anime titles are (used to pick the right
//!   TMDB match for a trailer)

use jni::objects::{JClass, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;

/// Convert an HTML snippet (as returned by AniList) into readable plain text.
pub fn strip_html(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let mut in_tag = false;
    let mut tag = String::new();

    for ch in input.chars() {
        if in_tag {
            if ch == '>' {
                in_tag = false;
                let t = tag.trim().trim_start_matches('/').to_ascii_lowercase();
                if t.starts_with("br") {
                    out.push('\n');
                } else if (t == "p" || t == "li" || t == "div") && !out.ends_with('\n') {
                    out.push('\n');
                }
                tag.clear();
            } else {
                tag.push(ch);
            }
        } else if ch == '<' {
            in_tag = true;
        } else {
            out.push(ch);
        }
    }

    let decoded = out
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .replace("&nbsp;", " ")
        .replace("&hellip;", "...")
        .replace("&mdash;", "-")
        .replace("&ndash;", "-");

    // Collapse runs of blank lines and trim every line.
    let mut result = String::with_capacity(decoded.len());
    let mut blank_run = 0;
    for line in decoded.lines() {
        let line = line.trim();
        if line.is_empty() {
            blank_run += 1;
            if blank_run > 1 {
                continue;
            }
        } else {
            blank_run = 0;
        }
        result.push_str(line);
        result.push('\n');
    }
    result.trim().to_string()
}

/// AniList average score (0..=100) -> one-decimal rating out of 10.
pub fn format_score(score: i32) -> String {
    if score <= 0 {
        return "N/A".to_string();
    }
    let clamped = score.min(100);
    format!("{}.{}", clamped / 10, clamped % 10)
}

/// Month (1..=12) -> AniList `MediaSeason` value.
pub fn season_for_month(month: i32) -> &'static str {
    match month {
        1 | 2 | 3 => "WINTER",
        4 | 5 | 6 => "SPRING",
        7 | 8 | 9 => "SUMMER",
        _ => "FALL",
    }
}

/// "in 3d 4h 12m 5s". Units that are zero at the front are dropped; "Airing now" when due.
pub fn format_countdown(secs: i64) -> String {
    if secs <= 0 {
        return "Airing now".to_string();
    }
    let d = secs / 86_400;
    let h = (secs % 86_400) / 3_600;
    let m = (secs % 3_600) / 60;
    let s = secs % 60;
    let mut out = String::from("in ");
    if d > 0 {
        out.push_str(&format!("{d}d "));
    }
    if d > 0 || h > 0 {
        out.push_str(&format!("{h}h "));
    }
    if d > 0 || h > 0 || m > 0 {
        out.push_str(&format!("{m}m "));
    }
    out.push_str(&format!("{s}s"));
    out
}

/// 1234 -> "1.2K", 3_400_000 -> "3.4M", small numbers unchanged.
pub fn compact_count(n: i32) -> String {
    if n >= 1_000_000 {
        format!("{:.1}M", n as f64 / 1_000_000.0).replace(".0M", "M")
    } else if n >= 1_000 {
        format!("{:.1}K", n as f64 / 1_000.0).replace(".0K", "K")
    } else {
        n.to_string()
    }
}

/// Orders video sources best first. `spec` is "height,latency_ms,hls;height,latency_ms,hls;...".
/// Returns the indexes of the entries, best first, as "2,0,1".
/// Score = up to 60 points for resolution (2160p = 60) + up to 40 for speed (0 ms = 40, 4 s = 0)
/// + 3 for HLS (adapts to the connection). Ties keep their original order.
pub fn rank_streams(spec: &str) -> String {
    let mut scored: Vec<(usize, f64)> = Vec::new();
    for (i, entry) in spec.split(';').filter(|e| !e.trim().is_empty()).enumerate() {
        let mut parts = entry.split(',').map(|p| p.trim());
        let height: f64 = parts.next().and_then(|p| p.parse().ok()).unwrap_or(0.0);
        let latency: f64 = parts.next().and_then(|p| p.parse().ok()).unwrap_or(4000.0);
        let hls = parts.next() == Some("1");
        let quality = height.clamp(0.0, 2160.0) / 2160.0 * 60.0;
        let speed = 40.0 * (1.0 - latency.clamp(0.0, 4000.0) / 4000.0);
        let bonus = if hls { 3.0 } else { 0.0 };
        scored.push((i, quality + speed + bonus));
    }
    scored.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal).then(a.0.cmp(&b.0)));
    scored.iter().map(|(i, _)| i.to_string()).collect::<Vec<_>>().join(",")
}

fn tokens(s: &str) -> Vec<String> {
    s.to_lowercase()
        .split(|c: char| !c.is_alphanumeric())
        .filter(|t| !t.is_empty())
        .map(str::to_string)
        .collect()
}

/// How alike two titles are, 0-100: word overlap (Jaccard), with a boost when one title's
/// words are all inside the other ("Attack on Titan" vs "Attack on Titan Season 2").
pub fn title_similarity(a: &str, b: &str) -> i32 {
    let ta = tokens(a);
    let tb = tokens(b);
    if ta.is_empty() || tb.is_empty() {
        return 0;
    }
    let inter = ta.iter().filter(|t| tb.contains(t)).count();
    let union = ta.len() + tb.iter().filter(|t| !ta.contains(t)).count();
    let jaccard = inter * 100 / union;
    let smaller = ta.len().min(tb.len());
    let contained = if inter == smaller { 85 } else { 0 };
    jaccard.max(contained) as i32
}

/// Instant, offline search over titles already on the device. `titles` holds one anime per
/// line, its names separated by tabs. Returns the line numbers of the best matches, best
/// first, as "3,17,4". Typing a piece of a name ("nar") matches; so do reordered words.
pub fn rank_titles(query: &str, titles: &str, limit: usize) -> String {
    let q = query.trim().to_lowercase();
    if q.is_empty() {
        return String::new();
    }
    let mut scored: Vec<(i32, usize)> = Vec::new();
    for (i, line) in titles.split('\n').enumerate() {
        let mut best = 0;
        for name in line.split('\t') {
            let n = name.to_lowercase();
            if n.is_empty() {
                continue;
            }
            let score = if n.starts_with(&q) {
                100
            } else if n.split(|c: char| !c.is_alphanumeric()).any(|w| w.starts_with(q.as_str())) {
                94
            } else if n.contains(&q) {
                88
            } else {
                title_similarity(&q, &n)
            };
            best = best.max(score);
        }
        if best >= 55 {
            scored.push((best, i));
        }
    }
    scored.sort_by(|a, b| b.0.cmp(&a.0).then(a.1.cmp(&b.1)));
    scored
        .iter()
        .take(limit)
        .map(|(_, i)| i.to_string())
        .collect::<Vec<_>>()
        .join(",")
}

/// Words that carry no identity for a season match.
fn is_filler(t: &str) -> bool {
    matches!(t, "season" | "part" | "cour" | "the" | "no" | "wa" | "ga" | "tv" | "anime" | "dub" | "sub" | "s")
}

/// "2nd" -> "2", "ii" stays; long digit runs (site ids) are dropped.
fn season_token(t: &str) -> Option<String> {
    let t = t.trim();
    if t.is_empty() || is_filler(t) {
        return None;
    }
    let digits: String = t.chars().take_while(|c| c.is_ascii_digit()).collect();
    let rest = &t[digits.len()..];
    if !digits.is_empty() && matches!(rest, "" | "st" | "nd" | "rd" | "th") {
        return if digits.len() > 2 && digits.len() != 4 { None } else { Some(digits) };
    }
    Some(t.to_string())
}

/// 0-100: do these two titles name the SAME season? Unlike `title_similarity` there is no
/// "one contains the other" boost, so "Show" never matches "Show: Second Arc" (that is how a
/// season 2 request ended up playing season 1). Extra words on either side lower the score.
pub fn season_match(a: &str, b: &str) -> i32 {
    let norm = |s: &str| -> Vec<String> {
        let mut v: Vec<String> = s
            .to_lowercase()
            .split(|c: char| !c.is_alphanumeric())
            .filter_map(season_token)
            .collect();
        v.sort();
        v.dedup();
        v
    };
    let ta = norm(a);
    let tb = norm(b);
    if ta.is_empty() || tb.is_empty() {
        return 0;
    }
    let inter = ta.iter().filter(|t| tb.contains(t)).count();
    let union = ta.len() + tb.iter().filter(|t| !ta.contains(t)).count();
    let score = (inter * 100 / union) as i32;
    // A season number on one side only ("Show 2" vs "Show") means a different season.
    let nums = |v: &Vec<String>| -> Vec<String> {
        v.iter().filter(|t| t.len() <= 2 && t.chars().all(|c| c.is_ascii_digit())).cloned().collect()
    };
    if nums(&ta) != nums(&tb) {
        score / 2
    } else {
        score
    }
}

fn to_jstring(env: &mut JNIEnv, value: &str) -> jstring {
    match env.new_string(value) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_biaruuu_kiro_core_NativeCore_stripHtml<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    input: JString<'local>,
) -> jstring {
    let text: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    to_jstring(&mut env, &strip_html(&text))
}

#[no_mangle]
pub extern "system" fn Java_com_biaruuu_kiro_core_NativeCore_formatScore<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    score: jint,
) -> jstring {
    to_jstring(&mut env, &format_score(score))
}

#[no_mangle]
pub extern "system" fn Java_com_biaruuu_kiro_core_NativeCore_seasonForMonth<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    month: jint,
) -> jstring {
    to_jstring(&mut env, season_for_month(month))
}

#[no_mangle]
pub extern "system" fn Java_com_biaruuu_kiro_core_NativeCore_titleSimilarity<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    a: JString<'local>,
    b: JString<'local>,
) -> jint {
    let a: String = match env.get_string(&a) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };
    let b: String = match env.get_string(&b) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };
    title_similarity(&a, &b)
}

#[no_mangle]
pub extern "system" fn Java_com_biaruuu_kiro_core_NativeCore_rankTitlesNative<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    query: JString<'local>,
    titles: JString<'local>,
    limit: jint,
) -> jstring {
    let q: String = match env.get_string(&query) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let t: String = match env.get_string(&titles) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    to_jstring(&mut env, &rank_titles(&q, &t, limit.max(1) as usize))
}

#[no_mangle]
pub extern "system" fn Java_com_biaruuu_kiro_core_NativeCore_seasonMatchNative<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    a: JString<'local>,
    b: JString<'local>,
) -> jint {
    let a: String = match env.get_string(&a) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };
    let b: String = match env.get_string(&b) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };
    season_match(&a, &b)
}

#[no_mangle]
pub extern "system" fn Java_com_biaruuu_kiro_core_NativeCore_formatCountdown<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    secs: jlong,
) -> jstring {
    to_jstring(&mut env, &format_countdown(secs))
}

#[no_mangle]
pub extern "system" fn Java_com_biaruuu_kiro_core_NativeCore_compactCount<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    n: jint,
) -> jstring {
    to_jstring(&mut env, &compact_count(n))
}

#[no_mangle]
pub extern "system" fn Java_com_biaruuu_kiro_core_NativeCore_rankStreamsNative<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    spec: JString<'local>,
) -> jstring {
    let spec: String = match env.get_string(&spec) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };
    to_jstring(&mut env, &rank_streams(&spec))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rank_titles_finds_partial_and_reordered() {
        let titles = "Naruto\tNaruto\nOne Piece\tOne Piece\nBoruto: Naruto Next Generations\tBoruto";
        assert_eq!(rank_titles("nar", titles, 5), "0,2");
        assert_eq!(rank_titles("piece one", titles, 5), "1");
        assert_eq!(rank_titles("", titles, 5), "");
        assert_eq!(rank_titles("zzz", titles, 5), "");
    }

    #[test]
    fn season_match_tells_seasons_apart() {
        assert!(season_match("Jujutsu Kaisen Season 2", "jujutsu-kaisen-2nd-season-18413") >= 90);
        assert!(season_match("Jujutsu Kaisen", "jujutsu-kaisen-2nd-season-18413") < 50);
        assert!(season_match("Jujutsu Kaisen", "jujutsu-kaisen-18413") >= 90);
        assert!(season_match("Demon Slayer: Entertainment District Arc", "demon-slayer-kimetsu-no-yaiba-1234") < 50);
        assert_eq!(season_match("", "x"), 0);
    }

    #[test]
    fn strips_tags_and_entities() {
        let s = "Hello <i>world</i>!<br><br>Tom &amp; Jerry &quot;ok&quot;";
        assert_eq!(strip_html(s), "Hello world!\n\nTom & Jerry \"ok\"");
    }

    #[test]
    fn collapses_blank_lines() {
        assert_eq!(strip_html("a<br><br><br><br>b"), "a\n\nb");
    }

    #[test]
    fn formats_scores() {
        assert_eq!(format_score(87), "8.7");
        assert_eq!(format_score(100), "10.0");
        assert_eq!(format_score(0), "N/A");
        assert_eq!(format_score(-5), "N/A");
    }

    #[test]
    fn maps_seasons() {
        assert_eq!(season_for_month(1), "WINTER");
        assert_eq!(season_for_month(5), "SPRING");
        assert_eq!(season_for_month(8), "SUMMER");
        assert_eq!(season_for_month(11), "FALL");
    }

    #[test]
    fn scores_titles() {
        assert_eq!(title_similarity("Attack on Titan", "attack on titan"), 100);
        assert!(title_similarity("Attack on Titan", "Attack on Titan Season 2") >= 85);
        assert_eq!(title_similarity("Naruto", "One Piece"), 0);
    }

    #[test]
    fn formats_countdowns() {
        assert_eq!(format_countdown(0), "Airing now");
        assert_eq!(format_countdown(5), "in 5s");
        assert_eq!(format_countdown(125), "in 2m 5s");
        assert_eq!(format_countdown(3 * 86_400 + 4 * 3_600 + 12 * 60 + 5), "in 3d 4h 12m 5s");
        assert_eq!(format_countdown(3_600), "in 1h 0m 0s");
    }

    #[test]
    fn compacts_counts() {
        assert_eq!(compact_count(999), "999");
        assert_eq!(compact_count(1_234), "1.2K");
        assert_eq!(compact_count(2_000), "2K");
        assert_eq!(compact_count(3_400_000), "3.4M");
    }

    #[test]
    fn ranks_streams() {
        // 1080p/slow vs 720p/fast vs 480p/very fast vs unknown
        assert_eq!(rank_streams("1080,200,0;720,100,0;480,50,0"), "0,1,2");
        // a 1080p source that takes 4s loses to a quick 720p one
        assert_eq!(rank_streams("1080,4000,0;720,100,0"), "1,0");
        // HLS bonus breaks an otherwise equal tie, equal scores keep input order
        assert_eq!(rank_streams("720,100,0;720,100,1"), "1,0");
        assert_eq!(rank_streams("720,100,0;720,100,0"), "0,1");
        assert_eq!(rank_streams(""), "");
    }
}
