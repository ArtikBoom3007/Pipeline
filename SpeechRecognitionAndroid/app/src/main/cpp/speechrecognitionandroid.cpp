#include <jni.h>
#include <vector>

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_lightbuzz_speechrecognitionandroid_MainActivity_processAudio(JNIEnv *env, jobject /* this */, jbyteArray audioData) {
    // Get the length of the array
    jsize length = env->GetArrayLength(audioData);

    // Obtain a pointer to the array's contents
    jbyte* buffer = env->GetByteArrayElements(audioData, JNI_FALSE);

    // Construct a std::vector from the byte array
    std::vector<uint8_t> cppVector(buffer, buffer + length);

    // Release the array elements
    env->ReleaseByteArrayElements(audioData, buffer, JNI_ABORT);

    // Process the data (example: simply copy it back)
    std::vector<uint8_t> processedData = cppVector; // Replace with your processing logic

    processedData[0] = 19;
    // Convert the processed data back to a Java byte array
    jbyteArray processedDataArray = env->NewByteArray(processedData.size());
    env->SetByteArrayRegion(processedDataArray, 0, processedData.size(), reinterpret_cast<jbyte*>(processedData.data()));

    return processedDataArray;
}