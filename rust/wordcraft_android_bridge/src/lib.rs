//! Android JNI adapter for the unmodified storytold/wordcraft 0.16.0 engine.
//!
//! The document model, commands, undo history, file formats and layout stay in
//! the upstream Rust engine. This crate only translates Android/JNI calls.

use std::sync::{Mutex, OnceLock};

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jint, jbyteArray, jstring};
use jni::JNIEnv;
use serde_json::{Value, json};
use wordcraft_engine::{Session, doc::Document};

static SESSION: OnceLock<Mutex<Session>> = OnceLock::new();

fn session() -> &'static Mutex<Session> {
    SESSION.get_or_init(|| Mutex::new(Session::new(Document::new())))
}

fn java_string(env: &mut JNIEnv<'_>, value: impl AsRef<str>) -> jstring {
    env.new_string(value.as_ref())
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Result<String, String> {
    env.get_string(value)
        .map(|value| value.to_string_lossy().into_owned())
        .map_err(|error| format!("Could not read Java string: {error}"))
}

fn json_result(result: Result<Value, impl std::fmt::Display>) -> String {
    match result {
        Ok(value) => json!({"ok": true, "result": value}).to_string(),
        Err(error) => json!({"ok": false, "error": error.to_string()}).to_string(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_engineStatus(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let status = match session().lock() {
        Ok(_) => "WordCraft Rust engine 0.16.0 is loaded",
        Err(_) => "WordCraft engine session lock failed",
    };
    java_string(&mut env, status)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeGetText(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let text = match session().lock() {
        Ok(current) => current.doc.plain_text(wordcraft_engine::doc::StoryRef::Body),
        Err(_) => String::new(),
    };
    java_string(&mut env, text)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeGetSelection(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let text = match session().lock() {
        Ok(current) => current.selected_text(),
        Err(_) => String::new(),
    };
    java_string(&mut env, text)
}

/// Dispatch any registered WordCraft command by stable command id and JSON params.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeRunCommand(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    command: JString<'_>,
    params_json: JString<'_>,
) -> jstring {
    let command = match read_string(&mut env, &command) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, json!({"ok": false, "error": error}).to_string()),
    };
    let params_text = match read_string(&mut env, &params_json) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, json!({"ok": false, "error": error}).to_string()),
    };
    let params: Value = match serde_json::from_str(&params_text) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, json!({"ok": false, "error": format!("Invalid command parameters: {error}")}).to_string()),
    };
    let result = match session().lock() {
        Ok(mut current) => json_result(current.run(&command, &params)),
        Err(_) => json!({"ok": false, "error": "WordCraft engine session lock failed"}).to_string(),
    };
    java_string(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeSetText(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    value: JString<'_>,
) -> jstring {
    let text = match read_string(&mut env, &value) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, error),
    };
    let result = match session().lock() {
        Ok(mut current) => current.run("document.setText", &json!({"text": text}))
            .map(|_| "Document text replaced by WordCraft".to_owned())
            .unwrap_or_else(|error| format!("WordCraft edit failed: {error}")),
        Err(_) => "WordCraft engine session lock failed".to_owned(),
    };
    java_string(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeFormatAll(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    command: JString<'_>,
) -> jstring {
    let command = match read_string(&mut env, &command) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, error),
    };
    let result = match session().lock() {
        Ok(mut current) => current.run("select.all", &json!({}))
            .and_then(|_| current.run(&command, &json!({})))
            .map(|_| format!("Applied {command} to the whole document"))
            .unwrap_or_else(|error| format!("{command} failed: {error}")),
        Err(_) => "WordCraft engine session lock failed".to_owned(),
    };
    java_string(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeUndo(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let result = match session().lock() {
        Ok(mut current) => current.run("edit.undo", &json!({}))
            .map(|_| "Undo completed".to_owned())
            .unwrap_or_else(|error| format!("Undo failed: {error}")),
        Err(_) => "WordCraft engine session lock failed".to_owned(),
    };
    java_string(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeRedo(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let result = match session().lock() {
        Ok(mut current) => current.run("edit.redo", &json!({}))
            .map(|_| "Redo completed".to_owned())
            .unwrap_or_else(|error| format!("Redo failed: {error}")),
        Err(_) => "WordCraft engine session lock failed".to_owned(),
    };
    java_string(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativePageCount(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jint {
    match session().lock() {
        Ok(mut current) => current.layout().pages.len().min(jint::MAX as usize) as jint,
        Err(_) => 0,
    }
}

/// Open any format supported by WordCraft's extension dispatcher, based on its filename.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeOpenDocument(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    name: JString<'_>,
    bytes: JByteArray<'_>,
) -> jstring {
    let name = match read_string(&mut env, &name) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, error),
    };
    let bytes = match env.convert_byte_array(bytes) {
        Ok(value) => value,
        Err(error) => return java_string(&mut env, format!("Could not read document bytes: {error}")),
    };
    let result = match wordcraft_engine::io::open_bytes(&name, &bytes) {
        Ok(document) => match session().lock() {
            Ok(mut current) => {
                current.set_document(document);
                format!("Opened {name} with WordCraft 0.16.0")
            }
            Err(_) => "WordCraft engine session lock failed".to_owned(),
        },
        Err(error) => format!("Could not open {name}: {error}"),
    };
    java_string(&mut env, result)
}

/// Export by extension: docx, pdf, rtf, odt and other engine-supported formats.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeSaveDocument(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    name: JString<'_>,
) -> jbyteArray {
    let name = match read_string(&mut env, &name) {
        Ok(value) => value,
        Err(_) => return std::ptr::null_mut(),
    };
    let bytes = match session().lock() {
        Ok(current) => match wordcraft_engine::io::save_bytes(&name, &current.doc) {
            Ok(bytes) => bytes,
            Err(_) => return std::ptr::null_mut(),
        },
        Err(_) => return std::ptr::null_mut(),
    };
    env.byte_array_from_slice(&bytes)
        .map(|array| array.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeGetLayoutSummary(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let summary = match session().lock() {
        Ok(mut current) => current.run("document.layout", &json!({}))
            .map(|value| value.to_string())
            .unwrap_or_else(|error| json!({"error": error.to_string()}).to_string()),
        Err(_) => json!({"error": "WordCraft engine session lock failed"}).to_string(),
    };
    java_string(&mut env, summary)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wordcraft_android_NativeBridge_nativeCommandCatalog(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    java_string(&mut env, wordcraft_engine::catalog::CATALOG)
}
