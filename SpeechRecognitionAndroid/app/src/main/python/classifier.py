import scipy.io.wavfile as wav
from python_speech_features import mfcc
import numpy as np
import joblib
import sklearn

def extract_features(file_path):
    global features

    sample_rate, signal = wav.read(file_path)

    mfcc_feat = mfcc(signal, sample_rate, nfft=1200, numcep=20)

    features = []
    features.extend([np.mean(e) for e in mfcc_feat.T])
    features.extend([np.std(e) for e in mfcc_feat.T])

    if features:
        print("features extract saccessfully")

    return features

def classify_audio(file_path):
    features = extract_features(file_path)
    
    prediction = model.predict([features])
    print("prediction", prediction)

    result = 1 if prediction[0]=="PD" else 0
    return int(result)

def load_model(model_path):
    global model
    model = joblib.load(model_path)
    print("Model loaded successfully!")

# def return_signal(file_path):
#     byte_array = array.array('B')
#     audio_file = open(file_path, 'rb')
#     byte_array.fromstring(audio_file.read())
#     print(len(byte_array))
#     audio_file.close()
#     return byte_array