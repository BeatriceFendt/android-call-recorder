import numpy as np
import matplotlib.pyplot as plt
import scipy.fftpack
import random

# Fe = sample rate
# N = samples count
def plot(Fe, N, x, y):
  plt.subplot(2, 1, 1)
  plt.plot(x, y)

  yf = scipy.fftpack.fft(y)
  xf = np.linspace(0.0, Fe/2, N/2)

  yf = 2.0/N * np.abs(yf[:N/2])
  plt.subplot(2, 1, 2)
  plt.plot(xf, yf)
  
  plt.show()

def noise(y, amp):
  return y + amp*np.random.sample(len(y))

def simple(Fe):
  N = Fe
  x = np.linspace(0.0, 1.0, N)
  y = 0.9 * np.sin(50.0 * 2.0*np.pi*x) + 0.5*np.sin(80.0 * 2.0*np.pi*x)
  
  #y = noise(y, 2)

  plot(Fe, N, x, y)

def real_sound_weave(freqHz):
  Fe = 16000
  durationMs = 100
  N = Fe * durationMs / 1000
  x = np.linspace(0.0, N, N)
  y = np.sin(2.0 * np.pi * x / (Fe / float(freqHz))) * 0x7FFF

  #y = noise(y, 0x7fff)

  plot(Fe, N, x, y)

simple(1000)
#real_sound_weave(4500)