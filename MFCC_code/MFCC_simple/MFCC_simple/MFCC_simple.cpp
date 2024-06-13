// MFCC_simple.cpp : Этот файл содержит функцию "main". Здесь начинается и заканчивается выполнение программы.
//

#include <iostream>
#include <stdio.h> // C
#include <string.h> // For memset
#include <fstream>
#include <cstdint>

#include "fftw3.h"

#include "libmfcc.c"
#include "libmfcc.h"

using namespace std;

int main()
{
	ifstream file("echo_ID00_hc_0_0_0(mp3cut.net)(1).wav_fragment_0.wav", ios::binary);
	if (file.is_open()) // вызов метода is_open()
		cout << "File opened\n\n" << endl;
	else
	{
		cout << "File did not opened\n\n" << endl;
		return -1;
	}
	file.seekg(44, ios_base::beg); //Стать на 45-й байт

    //Считаем 0.025с отладочного аудио
	int n = 2206;
	//Создаем буффер
	char* buffer1 = new char[n];
	//Считываем данные в массив
	double data1[1103] = {0};
	//Читаем в него байты
	file.read(buffer1, n);
	//выводим их на экран
	for (int i = 0; i < n; i += 2) {
		short int result = (static_cast<short int>(buffer1[i + 1]) << 8) | static_cast<short int>(buffer1[i]);
		data1[i / 2] = result;
		//cout << result << endl;
	}

	delete[] buffer1;
	file.close();


    // Размер массива данных
	const int N = 1103;

	// Создаем массивы для исходных данных и результатов
	fftw_complex out[N/2 + 1];

	//Создаем план для преобразования
	fftw_plan plan = fftw_plan_dft_r2c_1d(N, data1, out, FFTW_ESTIMATE);

	// Выполняем преобразование
	fftw_execute(plan);

	// Запишем в файл .dat
	ofstream outFile;
    outFile.open("data_fft.dat");

	// Выводим результаты
	cout << "FFT Results:" << endl;
	for (int i = 0; i < N/2 + 1; ++i) {
		cout << "out[" << i << "] = (" << out[i][0] << ", " << out[i][1] << ")" << endl;
		outFile << sqrt(pow(out[i][0], 2) + pow(out[i][1], 2)) << endl;
	}

	// Закрываем файл
	outFile.close();

	// Освобождаем память, выделенную под план
	fftw_destroy_plan(plan);



	
	
	
	//// Read in sample data from sample.dat
	//// sample.dat contains an 8192-point spectrum from a sine wave at 440Hz (A) in double precision
	//// Spectrum was computed using FFTW (http://www.fftw.org/)
	//// Data was not windowed (rectangular)

	// Holds the spectrum data to be analyzed
	double spectrum[N/2 + 1];

	// Pointer to the sample data file
	FILE* sampleFile;

	// Index counter - used to keep track of which data point is being read in
	int i = 0;

	// Determine which MFCC coefficient to compute
	unsigned int coeff;

	// Holds the value of the computed coefficient
	double mfcc_result;

	// Initialize the spectrum
	memset(&spectrum, 0, sizeof(spectrum));

	// Open the sample spectrum data	
	sampleFile = fopen("data_fft.dat", "rb");

	// Read in the contents of the sample file
	while (fscanf(sampleFile, "%lf", &spectrum[i]) != EOF) // %lf tells fscanf to read a double
	{
		i++;
	}

	// Close the sample file
	fclose(sampleFile);

	// Compute the first 13 coefficients
	for (coeff = 0; coeff < 13; coeff++)
	{
		mfcc_result = GetCoefficient(spectrum, 44100, 48, 552, coeff);
		printf("%i %f\n", coeff, mfcc_result);
	}
	getchar();

	return 0;
}


