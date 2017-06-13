#!/usr/bin/env python

import argparse
import dateutil.parser
import datetime
import subprocess
import tempfile
from glob import glob
from os import path, remove

import wave
import PIL.Image
import PIL.ExifTags

def parse_timestamp(file_path, strip_ext=True):
    basename= path.basename(file_path)
    if strip_ext:
        (basename,ex)= path.splitext(basename)
    return dateutil.parser.parse(basename)

def get_session_media(session_path, media_name):
    media_glob= path.join(session_path, media_name, "*")
    return [path.abspath(file) for file in sorted(glob(media_glob))]

def get_image_orientation(image_path):
    img = PIL.Image.open(image_path)

    exif = {
        PIL.ExifTags.TAGS[k]: v
        for k, v in img._getexif().items()
        if k in PIL.ExifTags.TAGS
    }

    orientation = exif['Orientation']
    if orientation == 6:
        return 90
    elif orientation == 3:
        return 180
    elif orientation == 8:
        return 270
    else:
        return 0

def get_audio_duration(wav_path):
    f= wave.open(wav_path, 'r')
    return f.getnframes() / float(f.getframerate())

# see http://ffmpeg.org/ffmpeg-formats.html#concat-1
#
def make_concat_script(image_files, audio_end_timestamp):
    concat_script= ['ffconcat version 1.0']

    file= image_files[0]
    last_timestamp= parse_timestamp(file)
    concat_script.append("file %s" % file)

    for file in image_files[1::]:
        timestamp= parse_timestamp(file)
        duration= (timestamp - last_timestamp).total_seconds()

        concat_script.append("duration %f" % duration)
        concat_script.append("file %s" % file)

        last_timestamp= timestamp

    # pad duration out to end of the audio
    duration= (audio_end_timestamp - last_timestamp).total_seconds()
    if (duration > 0):
        concat_script.append("duration %f" % duration)

    return '\n'.join(concat_script)

def mux_session(session_path, output_file):
    image_files= get_session_media(session_path, 'images')
    audio_file= get_session_media(session_path, 'audio')[0]

    orientation= get_image_orientation(image_files[0])

    image_timestamp= parse_timestamp(image_files[0])
    audio_timestamp= parse_timestamp(audio_file)
    audio_end_timestamp= audio_timestamp + datetime.timedelta(seconds=get_audio_duration(audio_file))
    session_timestamp= parse_timestamp(session_path, strip_ext=False)
    audio_delay= (audio_timestamp - image_timestamp).total_seconds()

    concat_script_path= path.join(tempfile.gettempdir(), "concat_script.txt")
    with open(concat_script_path, 'w') as file:
        file.write(make_concat_script(image_files, audio_end_timestamp))

    temp_video_path= path.join(tempfile.gettempdir(), "video.mp4")

    command= [
        "ffmpeg",

        "-safe", "0",                       # allow absolute file paths in concat script
        "-i", concat_script_path,

        "-itsoffset", "%f" % audio_delay,   # push the audio forward/backward
        "-i", audio_file,

        "-metadata",
        "title=%s" % path.basename(session_path),

        "-vf", "fps=30",

        "-y",
        temp_video_path
    ]
    print " ".join(command)
    subprocess.call(command)

    # ffconcat seems to ignore rotation metadata, so apply that in a separate step
    command= [
        "ffmpeg",

        "-i",
        temp_video_path,

        "-metadata:s:v:0",
        "rotate=%d" % (360-orientation),

        "-codec",
        "copy",

        "-y",
        output_file    
    ]
    print " ".join(command)
    subprocess.call(command)

    # remote temp files
    remove(concat_script_path)
    remove(temp_video_path)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sessions", type=str, nargs='+')
    args= parser.parse_args()
    
    for session in args.sessions:    
        output= path.basename(session) + '.mp4'
        mux_session(session, output)

if __name__ == "__main__":
    main()
