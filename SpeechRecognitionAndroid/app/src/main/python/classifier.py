import scipy.io.wavfile as wav
import python_speech_features as psf
import numpy as np
from joblib import load

def extract_features(file_path):
    global features

    num_filters = 26

    sample_rate, signal = wav.read(file_path)

   # Calculate the mfcc features based on the file data
    mfcc_feat = psf.mfcc(signal, sample_rate, nfft=1200, numcep=num_filters)

    num_filters = mfcc_feat.shape[1]
    # Извлечение Delta MFCC
    delta_feat = psf.delta(mfcc_feat, 2)

    # Извлечение Delta-Delta MFCC
    delta_delta_feat = psf.delta(delta_feat, 2)

    logfbank_feat = psf.logfbank(signal, sample_rate, nfft=1200)

    # Извлечение энергии
    energy = np.sum(mfcc_feat**2) / len(mfcc_feat)

    # Извлечение Zero Crossing Rate
    zcr = ((signal[:-1] * signal[1:]) < 0).sum() / len(signal)
    # Calculate the filterbank from the audio file

    mfcc_feat_mean = np.mean(mfcc_feat, axis=0)
    delta_feat_mean = np.mean(delta_feat, axis=0)
    delta_delta_feat_mean = np.mean(delta_delta_feat, axis=0)
    logfbank_feat_mean = np.mean(logfbank_feat, axis=0)

    mfcc_feat_std = np.std(mfcc_feat, axis=0)
    delta_feat_std = np.std(delta_feat, axis=0)
    delta_delta_feat_std = np.std(delta_delta_feat, axis=0)
    logfbank_feat_std = np.std(logfbank_feat, axis=0)
    
    
    features = np.hstack([
        mfcc_feat_mean, 
        delta_feat_mean, 
        delta_delta_feat_mean, 
        logfbank_feat_mean,
        mfcc_feat_std,
        delta_feat_std,
        delta_delta_feat_std,
        logfbank_feat_std,
        energy, 
        zcr
    ])

    features = scaler.transform(features.reshape(1, -1))
    
    if features.any():
        print("features extract saccessfully")

    return features

def classify_audio(file_path):
    
    features = extract_features(file_path)
    
    prediction = model_py.predict(features)
        
    print("prediction", prediction)

    return int(prediction[0])

def predict_from_features(feat):
    prediction = model_c.predict([feat])

    print("c predicrion", prediction)
    return int(prediction[0])
    

def load_model(model_path, model_c_path, scaler_path):
    global model_py, model_c, scaler
    model_py = load(model_path)
    model_c = load(model_c_path)
    scaler = load(scaler_path)
    print("Model loaded successfully!")

# def return_signal(file_path):
#     byte_array = array.array('B')
#     audio_file = open(file_path, 'rb')
#     byte_array.fromstring(audio_file.read())
#     print(len(byte_array))
#     audio_file.close()
#     return byte_array