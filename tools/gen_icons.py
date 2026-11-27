#!/usr/bin/env python3
"""Generates vector icons, shape drawables and launcher icons for Noor Player."""
import os
import sys
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "res")
DRAW = os.path.join(RES, "drawable")
os.makedirs(DRAW, exist_ok=True)

# Material Design icons (Apache 2.0), 24x24 viewport.
ICONS = {
    "ic_play": ["M8,5v14l11,-7z"],
    "ic_pause": ["M6,19h4V5H6v14zM14,5v14h4V5h-4z"],
    "ic_replay": ["M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z"],
    "ic_skip_next": ["M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z"],
    "ic_skip_previous": ["M6,6h2v12H6zM9.5,12l8.5,6V6z"],
    "ic_back": ["M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"],
    "ic_lock": ["M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"],
    "ic_lock_open": ["M12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6h1.9c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM18,20H6V10h12v10z"],
    "ic_rotate": ["M16.48,2.52c3.27,1.55 5.61,4.72 5.97,8.48h1.5C23.44,4.84 18.29,0 12,0l-0.66,0.03 3.81,3.81 1.33,-1.32zM10.23,1.75c-0.59,-0.59 -1.54,-0.59 -2.12,0L1.75,8.11c-0.59,0.59 -0.59,1.54 0,2.12l12.02,12.02c0.59,0.59 1.54,0.59 2.12,0l6.36,-6.36c0.59,-0.59 0.59,-1.54 0,-2.12L10.23,1.75zM14.83,21.19L2.81,9.17l6.36,-6.36 12.02,12.02 -6.36,6.36zM7.52,21.48C4.25,19.94 1.91,16.76 1.55,13H0.05C0.56,19.16 5.71,24 12,24l0.66,-0.03 -3.81,-3.81 -1.33,1.32z"],
    "ic_aspect": ["M19,12h-2v3h-3v2h5v-5zM7,9h3V7H5v5h2V9zM21,3H3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM21,19.01H3V4.99h18v14.02z"],
    "ic_pip": ["M19,11h-8v6h8v-6zM23,19V4.98C23,3.88 22.1,3 21,3H3C1.9,3 1,3.88 1,4.98V19c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2zM21,19.02H3V4.97h18v14.05z"],
    "ic_subtitles": ["M20,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM4,12h4v2H4v-2zM14,18H4v-2h10v2zM20,18h-4v-2h4v2zM20,14H10v-2h10v2z"],
    "ic_audiotrack": ["M12,3v9.28c-0.47,-0.17 -0.97,-0.28 -1.5,-0.28C8.01,12 6,14.01 6,16.5S8.01,21 10.5,21c2.31,0 4.2,-1.75 4.45,-4H15V6h4V3h-7z"],
    "ic_speed": ["M20.38,8.57l-1.23,1.85a8,8 0,0 1,-0.22 7.58H5.07A8,8 0,0 1,15.58 6.85l1.85,-1.23A10,10 0,0 0,3.35 19a2,2 0,0 0,1.72 1h13.85a2,2 0,0 0,1.74 -1a10,10 0,0 0,-0.27 -10.44zM10.59,15.41a2,2 0,0 0,2.83 0l5.66,-8.49 -8.49,5.66a2,2 0,0 0,0 2.83z"],
    "ic_more_vert": ["M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"],
    "ic_search": ["M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"],
    "ic_sort": ["M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z"],
    "ic_close": ["M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"],
    "ic_folder": ["M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"],
    "ic_add": ["M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"],
    "ic_link": ["M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z"],
    "ic_movie": ["M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z"],
    "ic_brightness": ["M20,15.31L23.31,12 20,8.69V4h-4.69L12,0.69 8.69,4H4v4.69L0.69,12 4,15.31V20h4.69L12,23.31 15.31,20H20v-4.69zM12,18V6c3.31,0 6,2.69 6,6s-2.69,6 -6,6z"],
    "ic_volume": ["M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"],
    "ic_volume_off": ["M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z"],
    "ic_camera": ["M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z",
                  "M8.8,12a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0z"],
    "ic_repeat": ["M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z"],
    "ic_repeat_one": ["M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4zM13,15V9h-1l-2,1v1h1.5v4H13z"],
    "ic_timer": ["M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"],
    "ic_info": ["M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z"],
    "ic_history": ["M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z"],
    "ic_settings": ["M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"],
    "ic_share": ["M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"],
    "ic_refresh": ["M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"],
    "ic_fast_forward": ["M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z"],
    "ic_fast_rewind": ["M11,18V6l-8.5,6 8.5,6zM11.5,12l8.5,6V6l-8.5,6z"],
    "ic_notification": ["M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,16.5v-9l6,4.5 -6,4.5z"],
    "ic_check_circle": ["M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"],
    "ic_circle_outline": ["M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z"],
    "ic_delete": ["M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"],
    "ic_select_all": ["M3,5h2V3c-1.1,0 -2,0.9 -2,2zM3,13h2v-2H3v2zM7,21h2v-2H7v2zM3,9h2V7H3v2zM13,3h-2v2h2V3zM19,3v2h2c0,-1.1 -0.9,-2 -2,-2zM5,21v-2H3c0,1.1 0.9,2 2,2zM3,17h2v-2H3v2zM9,3H7v2h2V3zM11,21h2v-2h-2v2zM19,13h2v-2h-2v2zM19,21c1.1,0 2,-0.9 2,-2h-2v2zM19,9h2V7h-2v2zM19,17h2v-2h-2v2zM15,21h2v-2h-2v2zM15,5h2V3h-2v2zM7,17h10V7H7v10zM9,9h6v6H9V9z"],
    "ic_playlist_play": ["M19,9H2v2h17V9zM19,5H2v2h17V5zM2,15h13v-2H2v2zM17,13v6l5,-3 -5,-3z"],
    "ic_touch_hold": ["M9,11.24V7.5C9,6.12 10.12,5 11.5,5S14,6.12 14,7.5v3.74c1.21,-0.81 2,-2.18 2,-3.74C16,5.01 13.99,3 11.5,3S7,5.01 7,7.5c0,1.56 0.79,2.93 2,3.74zM18.84,15.87l-4.54,-2.26c-0.17,-0.07 -0.35,-0.11 -0.54,-0.11H13v-6c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,6.67 10,7.5v10.74l-3.43,-0.72c-0.08,-0.01 -0.15,-0.03 -0.24,-0.03 -0.31,0 -0.59,0.13 -0.79,0.33l-0.79,0.8 4.94,4.94c0.27,0.27 0.65,0.44 1.06,0.44h6.79c0.75,0 1.33,-0.55 1.44,-1.28l0.75,-5.27c0.01,-0.07 0.02,-0.14 0.02,-0.2 0,-0.62 -0.38,-1.16 -0.91,-1.38z"],
    "ic_zoom": ["M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14zM12,10h-2v2H9v-2H7V9h2V7h1v2h2v1z"],
}
AUTO_MIRRORED = {"ic_back"}


def vector(name, paths):
    mirrored = ' android:autoMirrored="true"' if name in AUTO_MIRRORED else ""
    body = "\n".join('    <path android:fillColor="#FFFFFFFF" android:pathData="%s"/>' % p for p in paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp"\n'
            '    android:viewportWidth="24" android:viewportHeight="24"%s>\n%s\n</vector>\n') % (mirrored, body)


def circle(cx, cy, r):
    return "M%g,%ga%g,%g 0,1 1,%g,0a%g,%g 0,1 1,%g,0z" % (cx - r, cy, r, r, 2 * r, r, r, -2 * r)


SHAPES = {
    "bg_thumb": '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
                '    <solid android:color="@color/surface2"/>\n    <corners android:radius="8dp"/>\n</shape>\n',
    "bg_badge": '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
                '    <solid android:color="#CC000000"/>\n    <corners android:radius="4dp"/>\n</shape>\n',
    "bg_panel": '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
                '    <solid android:color="@color/panel"/>\n    <corners android:radius="16dp"/>\n</shape>\n',
    "bg_subtitle": '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
                   '    <solid android:color="#73000000"/>\n    <corners android:radius="6dp"/>\n</shape>\n',
    "bg_circle": '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">\n'
                 '    <solid android:color="#66000000"/>\n</shape>\n',
    "bg_pill": '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
               '    <solid android:color="#E6191C22"/>\n    <corners android:radius="24dp"/>\n</shape>\n',
    "bg_search": '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
                 '    <solid android:color="@color/surface"/>\n    <corners android:radius="24dp"/>\n</shape>\n',
    "scrim_top": '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
                 '    <gradient android:angle="270" android:startColor="#B3000000" android:endColor="#00000000"/>\n</shape>\n',
    "scrim_bottom": '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
                    '    <gradient android:angle="90" android:startColor="#CC000000" android:endColor="#00000000"/>\n</shape>\n',
}


def launcher_vectors():
    fg = ('<?xml version="1.0" encoding="utf-8"?>\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="108dp" android:height="108dp"\n'
          '    android:viewportWidth="108" android:viewportHeight="108">\n'
          '    <path android:fillColor="#40FFB300" android:pathData="%s"/>\n'
          '    <path android:fillColor="#FFFFB300" android:pathData="%s"/>\n'
          '    <path android:fillColor="#FF141833" android:pathData="M47,41L69,54L47,67Z"/>\n'
          '</vector>\n') % (circle(54, 54, 33), circle(54, 54, 27))
    mono = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            '    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="%s M47,41L69,54L47,67Z"/>\n'
            '</vector>\n') % circle(54, 54, 27)
    logo = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="28dp" android:height="28dp"\n'
            '    android:viewportWidth="24" android:viewportHeight="24">\n'
            '    <path android:fillColor="#FFFFB300" android:pathData="%s"/>\n'
            '    <path android:fillColor="#FF141833" android:pathData="M10,7.5L16.5,12L10,16.5Z"/>\n'
            '</vector>\n') % circle(12, 12, 10)
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/launcher_bg"/>\n'
                '    <foreground android:drawable="@drawable/ic_launcher_fg"/>\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_mono"/>\n'
                '</adaptive-icon>\n')
    return fg, mono, logo, adaptive


def draw_legacy(size, round_shape):
    s = size * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    navy = (0x14, 0x18, 0x33, 255)
    amber = (0xFF, 0xB3, 0x00, 255)
    pad = int(s * 0.04)
    if round_shape:
        d.ellipse([pad, pad, s - pad, s - pad], fill=navy)
    else:
        d.rounded_rectangle([pad, pad, s - pad, s - pad], radius=int(s * 0.22), fill=navy)
    c = s / 2.0
    glow = s * 0.36
    # glow ring: amber at 25% over the navy background, pre-blended so the PNG has no see-through pixels
    a = 0.25
    blend = tuple(int(round(n * (1 - a) + m * a)) for n, m in zip(navy[:3], amber[:3])) + (255,)
    d.ellipse([c - glow, c - glow, c + glow, c + glow], fill=blend)
    r = s * 0.30
    d.ellipse([c - r, c - r, c + r, c + r], fill=amber)
    # play triangle, same proportions as the vector (relative to the r=27 circle)
    k = r / 27.0
    tri = [(c + (47 - 54) * k, c + (41 - 54) * k), (c + (69 - 54) * k, c), (c + (47 - 54) * k, c + (67 - 54) * k)]
    d.polygon(tri, fill=navy)
    return img.resize((size, size), Image.LANCZOS)


def main():
    for name, paths in ICONS.items():
        with open(os.path.join(DRAW, name + ".xml"), "w") as f:
            f.write(vector(name, paths))
    for name, xml in SHAPES.items():
        with open(os.path.join(DRAW, name + ".xml"), "w") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n' + xml)
    fg, mono, logo, adaptive = launcher_vectors()
    with open(os.path.join(DRAW, "ic_launcher_fg.xml"), "w") as f:
        f.write(fg)
    with open(os.path.join(DRAW, "ic_launcher_mono.xml"), "w") as f:
        f.write(mono)
    with open(os.path.join(DRAW, "ic_logo.xml"), "w") as f:
        f.write(logo)
    any26 = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(any26, exist_ok=True)
    for n in ("ic_launcher", "ic_launcher_round"):
        with open(os.path.join(any26, n + ".xml"), "w") as f:
            f.write(adaptive)
    for folder, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        out = os.path.join(RES, "mipmap-" + folder)
        os.makedirs(out, exist_ok=True)
        draw_legacy(px, False).save(os.path.join(out, "ic_launcher.png"), optimize=True)
        draw_legacy(px, True).save(os.path.join(out, "ic_launcher_round.png"), optimize=True)
    # a large preview for checking the design
    preview = os.path.join(ROOT, "build", "preview")
    os.makedirs(preview, exist_ok=True)
    draw_legacy(512, False).save(os.path.join(preview, "launcher_512.png"))
    print("icons:", len(ICONS), "shapes:", len(SHAPES))


if __name__ == "__main__":
    sys.exit(main())
