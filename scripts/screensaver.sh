#!/usr/bin/env bash
# Shared by launcher/screensaver.sh and install.sh --undo. Requires sh_() and installed().

WIDGET_DREAM=tv.dumbtv.home/.Screensaver

enable_widget_screensaver() {
  installed tv.dumbtv.home || { echo "error: install Hyrule Home first" >&2; return 1; }
  sh_ cmd package query-services --brief -a android.service.dreams.DreamService | grep -F "$WIDGET_DREAM" >/dev/null || {
    echo "error: update Hyrule Home to a build with the widget screensaver first" >&2; return 1;
  }
  local saved key value got
  saved=$(sh_ settings get secure dumbtv_screensaver_saved | tr -d '\r')
  if [[ $saved != 1 ]]; then
    for key in screensaver_components screensaver_enabled; do
      value=$(sh_ settings get secure "$key" | tr -d '\r')
      sh_ settings put secure "dumbtv_before_$key" "${value:-null}"
    done
    sh_ settings put secure dumbtv_screensaver_saved 1
  fi
  sh_ settings put secure screensaver_components "$WIDGET_DREAM"
  got=$(sh_ settings get secure screensaver_components | tr -d '\r')
  [[ $got == "$WIDGET_DREAM" ]] || { echo "error: TV did not select the widget screensaver" >&2; return 1; }
  sh_ settings put secure screensaver_enabled 1
  [[ $(sh_ settings get secure screensaver_enabled | tr -d '\r') == 1 ]] || {
    echo "error: TV did not enable its screensaver" >&2; return 1;
  }
}

restore_widget_screensaver() {
  local saved current key value
  saved=$(sh_ settings get secure dumbtv_screensaver_saved | tr -d '\r')
  [[ $saved == 1 ]] || return 0
  current=$(sh_ settings get secure screensaver_components | tr -d '\r')
  # A later selection made in the TV's settings belongs to the owner.
  if [[ $current == "$WIDGET_DREAM" || $current == tv.dumbtv.home/tv.dumbtv.home.Screensaver ]]; then
    for key in screensaver_components screensaver_enabled; do
      # If the owner switched the screensaver off, keep that choice.
      if [[ $key == screensaver_enabled && $(sh_ settings get secure "$key" | tr -d '\r') != 1 ]]; then continue; fi
      value=$(sh_ settings get secure "dumbtv_before_$key" | tr -d '\r')
      if [[ -z $value || $value == null ]]; then sh_ settings delete secure "$key"
      else sh_ settings put secure "$key" "$value"; fi
    done
  fi
  sh_ settings delete secure dumbtv_before_screensaver_components
  sh_ settings delete secure dumbtv_before_screensaver_enabled
  sh_ settings delete secure dumbtv_screensaver_saved
}
