# Madzhab Oximeter: An Android-Based Oximeter Application
## Introduction
Madzhab Oximeter is an application for calculating oxygen saturation (SPO2) by using the smartphone's camera. The app records your finger via camera for 5 seconds, and then it calculates the SPO2 based on the red and blue channel from the video. This repository contains the prototype, which is on a jupyter notebook format, and also the application itself that is based on Android (Java). 

## Implementation
This app is implemented using: 
- Android Studio for developing the app,
- Java as the programming language, 
- OpenCV for processing the video, 
- FFmpeg for extracting frames from the video, and 
- JavaCV as the connector to the OpenCV and FFmpeg.

## References
[1] A. K. Kanva, C. J. Sharma and S. Deb, "Determination of SpO2 and heart-rate using smartphone camera," Proceedings of The 2014 International Conference on Control, Instrumentation, Energy and Communication (CIEC), 2014, pp. 237-241, doi: 10.1109/CIEC.2014.6959086.<br>
[2] Scully, C. G., Lee, J., Meyer, J., Gorbach, A. M., Granquist-Fraser, D., Mendelson, Y., & Chon, K. H. (2012). Physiological parameter monitoring from optical recordings with a mobile phone. IEEE transactions on bio-medical engineering, 59(2), 303–306. https://doi.org/10.1109/TBME.2011.2163157