#include <jni.h>
#include <algorithm>
#include <cstring>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>
#include "pinyinime.h"

namespace {
std::mutex mutex;
bool opened = false;
std::string utf8(JNIEnv* e, jstring s) {
    const char* raw=e->GetStringUTFChars(s,nullptr);
    std::string out(raw);e->ReleaseStringUTFChars(s,raw);return out;
}
}
extern "C" JNIEXPORT jboolean JNICALL
Java_app_doudou_ime_input_engine_PinyinEngine_nativeOpen(JNIEnv* e,jclass,jstring dict,jstring user) {
    std::lock_guard<std::mutex> lock(mutex);
    if(opened)ime_pinyin::im_close_decoder();
    opened=ime_pinyin::im_open_decoder(utf8(e,dict).c_str(),utf8(e,user).c_str());
    if(opened)ime_pinyin::im_set_max_lens(39,32);
    return opened;
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_doudou_ime_input_engine_PinyinEngine_nativeCandidates(JNIEnv* e,jclass,jstring input,jint offset,jint limit) {
    std::lock_guard<std::mutex> lock(mutex);
    jclass stringClass=e->FindClass("java/lang/String");
    std::string raw=utf8(e,input);
    if(!opened||raw.empty()||raw.size()>39||offset<0||limit<=0)return e->NewObjectArray(0,stringClass,nullptr);
    ime_pinyin::im_reset_search();size_t count=ime_pinyin::im_search(raw.c_str(),raw.size());
    size_t decoded=0;ime_pinyin::im_get_sps_str(&decoded);
    if(count==0||decoded==0)return e->NewObjectArray(0,stringClass,nullptr);
    const unsigned short* positions=nullptr;
    size_t spellings=ime_pinyin::im_get_spl_start_pos(positions);
    std::vector<std::pair<std::u16string,int>> rows;
    const size_t first=std::min(count,static_cast<size_t>(offset));
    const size_t end=first+std::min(count-first,static_cast<size_t>(limit));
    for(size_t i=first;i<end;i++){
        ime_pinyin::char16 text[256]={0};
        if(!ime_pinyin::im_get_candidate(i,text,256))continue;
        size_t len=0;while(len<255&&text[len])len++;
        int consumed=static_cast<int>(decoded);
        if(i>0&&positions&&len<=spellings)consumed=positions[len];
        if(consumed<=0||consumed>static_cast<int>(raw.size()))continue;
        rows.emplace_back(std::u16string(reinterpret_cast<char16_t*>(text),len),consumed);
    }
    jobjectArray out=e->NewObjectArray(rows.size()*2,stringClass,nullptr);
    for(size_t i=0;i<rows.size();i++){
        jstring text=e->NewString(reinterpret_cast<const jchar*>(rows[i].first.data()),rows[i].first.size());
        jstring code=e->NewStringUTF(raw.substr(0,rows[i].second).c_str());
        e->SetObjectArrayElement(out,i*2,text);e->SetObjectArrayElement(out,i*2+1,code);e->DeleteLocalRef(text);e->DeleteLocalRef(code);
    }
    return out;
}
extern "C" JNIEXPORT void JNICALL
Java_app_doudou_ime_input_engine_PinyinEngine_nativeLearn(JNIEnv* e,jclass,jstring input,jstring selected) {
    std::lock_guard<std::mutex> lock(mutex);if(!opened)return;
    std::string raw=utf8(e,input);ime_pinyin::im_reset_search();size_t n=ime_pinyin::im_search(raw.c_str(),raw.size());
    const jchar* chosen=e->GetStringChars(selected,nullptr);size_t chosenLen=e->GetStringLength(selected);
    for(size_t i=0;i<n;i++){
        ime_pinyin::char16 text[256]={0};if(!ime_pinyin::im_get_candidate(i,text,256))continue;
        size_t len=0;while(len<255&&text[len])len++;
        if(len==chosenLen&&memcmp(text,chosen,len*2)==0){ime_pinyin::im_choose(i);ime_pinyin::im_flush_cache();break;}
    }
    e->ReleaseStringChars(selected,chosen);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_app_doudou_ime_input_engine_PinyinEngine_nativeClear(JNIEnv* e,jclass,jstring dict,jstring user) {
    std::lock_guard<std::mutex> lock(mutex);
    if(opened)ime_pinyin::im_close_decoder();
    std::string path=utf8(e,user);std::remove(path.c_str());
    opened=ime_pinyin::im_open_decoder(utf8(e,dict).c_str(),path.c_str());
    if(opened)ime_pinyin::im_set_max_lens(39,32);
    return opened;
}
