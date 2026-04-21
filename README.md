# Mobile and Social Sensing Systems

Real-time vehicle and activity detection using smartphone sensors.

## Overview

This project presents a framework for developing a portable machine learning system for **vehicle and activity recognition on mobile devices**. The system uses **smartphone accelerometer data** to classify how the user is moving in real time.

A classification model based on a **Multi-Layer Perceptron (MLP)** was designed and trained using sensor data collected from a mobile phone. The application is able to detect both **transportation mode** and **physical activity**, including:

- Bus
- Scooter
- Bike
- Walking
- Running

The project focuses on a lightweight sensing approach by relying mainly on the **accelerometer sensor**, which helps reduce battery consumption while still providing good response time. :contentReference[oaicite:1]{index=1}

## Main Idea

The goal of the project is to build an intelligent mobile sensing system that can understand the user’s motion context using only smartphone sensor data.

To improve performance, the project includes:

- an efficient data processing pipeline
- different data collection strategies
- frequency-domain feature extraction
- noise reduction using **FFT**
- machine learning models trained for mobile activity classification

According to the repository description, the reported results achieved **over 95% accuracy** on a limited dataset. :contentReference[oaicite:2]{index=2}

## Features

- Real-time vehicle and activity detection
- Smartphone accelerometer-based sensing
- Lightweight mobile sensing design
- Multi-class classification for transport and movement modes
- Noise reduction using FFT
- Machine learning training pipeline
- Android application support for both data collection and monitoring
- Project documentation and presentation materials included in the repository :contentReference[oaicite:3]{index=3}

## Detected Classes

The system is designed to classify the following user states:

- Bus
- Scooter
- Bike
- Walking
- Running

These classes cover both **motorized** and **non-motorized** movement types. :contentReference[oaicite:4]{index=4}

## Methodology

The workflow of the project can be summarized as follows:

1. Collect accelerometer data using a mobile application
2. Preprocess and clean the sensor signals
3. Apply **FFT-based filtering / frequency extraction**
4. Build and train a machine learning classifier
5. Deploy the trained model for real-time inference on mobile devices

The repository description explicitly mentions the use of:

- accelerometer sensor data
- extracted frequencies
- a Multi-Layer Perceptron model
- FFT for noise removal and data optimization :contentReference[oaicite:5]{index=5}

## Project Structure

The repository currently includes the following main components:

```text
.
├── ML part/
├── Machine Learning/
├── app/
├── gradle/
├── build.gradle
├── settings.gradle
├── project_documentation.pdf
├── proposal.pdf
└── Real-Time Vehicle and Activity Detection using Smartphone Sensors .pptx
```

From the repository structure, the project appears to combine:

- machine learning experimentation
- mobile application development
- Gradle-based Android or Java-side project setup
- project documentation and presentation material :contentReference[oaicite:6]{index=6}

## Tech Stack

Based on the repository contents and detected languages:

- Jupyter Notebook
- Java
- Python
- Gradle
- Android application module
- Machine Learning pipeline

GitHub reports the main repository languages as mostly **Jupyter Notebook**, followed by **Java** and **Python**. :contentReference[oaicite:7]{index=7}

## Mobile Application

The project includes an application with two main modes:

- **data collection mode** for gathering accelerometer samples used in training
- **monitoring mode** for testing the deployed model in real time

This allows the system to support both dataset generation and live prediction inside the same project ecosystem. :contentReference[oaicite:8]{index=8}

## Design Goals

This project was developed with the following goals:

- detect user transportation mode and activity in real time
- minimize battery consumption by relying only on the accelerometer
- improve model performance through signal processing and data optimization
- create a portable sensing framework suitable for mobile devices
- combine data collection, training, and deployment in one workflow

## Results

According to the repository description:

- the model achieved **over 95% accuracy**
- FFT-based noise removal improved data quality and model performance
- overlap-based training experiments were also explored to improve accuracy further :contentReference[oaicite:9]{index=9}

## Repository Contents

The repository also includes supporting materials such as:

- `project_documentation.pdf`
- `proposal.pdf`
- `Real-Time Vehicle and Activity Detection using Smartphone Sensors .pptx`

These files suggest the repository contains not only code, but also technical documentation and presentation material. :contentReference[oaicite:10]{index=10}

## Notes

This project represents a mobile sensing and machine learning system for recognizing transportation mode and user activity using smartphone sensors. It combines real-time sensing, signal processing, model training, and mobile-side deployment in a single repository. :contentReference[oaicite:11]{index=11}

## License

This project is licensed under the MIT License.
