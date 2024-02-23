/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
*/

package com.yakovlevegor.DroidRec;

import androidx.preference.ListPreference;
import android.content.Context;
import android.util.AttributeSet;
import android.media.MediaCodecList;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.util.ArrayList;
// 오디오 코덱 목록 선택
public class CodecList extends ListPreference {

    private ArrayList<String> codecsList = new ArrayList<String>();

    private String prefName;

    private void getAllCodecs() {
        String codecMime = MediaFormat.MIMETYPE_VIDEO_AVC;

        if (prefName == "audiocodecvalue") {
            codecMime = MediaFormat.MIMETYPE_AUDIO_AAC;
        }


        MediaCodecList listCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] allCodecsList = listCodecs.getCodecInfos();
        int numCodecs = allCodecsList.length;
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = allCodecsList[i];

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(codecMime)) {
                    codecsList.add(codecInfo.getName());
                }
            }
        }
    }

    public CodecList(Context context, AttributeSet attrs) {
        super(context, attrs);
        prefName = getKey();
        String autoEntry = context.getResources().getString(R.string.codec_option_auto);
        String autoValue = context.getResources().getString(R.string.codec_option_auto_value);

        if (prefName == "audiocodecvalue") {
            autoEntry = context.getResources().getString(R.string.audio_codec_option_auto);
            autoValue = context.getResources().getString(R.string.audio_codec_option_auto_value);
        }

        getAllCodecs();
        String[] codecsAllEntries = new String[codecsList.size()+1];
        codecsAllEntries[0] = autoEntry;
        for (int i = 0; i < codecsList.size(); i++) {
            codecsAllEntries[i+1] = codecsList.get(i);
        }
        String[] codecsAllValues = new String[codecsList.size()+1];
        codecsAllValues[0] = autoValue;
        for (int i = 0; i < codecsList.size(); i++) {
            codecsAllValues[i+1] = codecsList.get(i);
        }
        setEntries(codecsAllEntries);
        setEntryValues(codecsAllValues);
        setDefaultValue(autoValue);
    }

}
