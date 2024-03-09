using System;
using System.Collections;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Speech.Synthesis;
using System.Text.RegularExpressions;
using System.Windows.Forms;
using NAudio.CoreAudioApi;
using NAudio.Lame;
using NAudio.Mixer;
using NAudio.Wave;
using NAudio.Wave.SampleProviders;
using OnlyR.Core.Enums;
using OnlyR.Core.EventArgs;
using OnlyR.Core.Models;
using OnlyR.Core.Properties;
using OnlyR.Core.Samples;
using TorchSharp;
using TorchSharp.Modules;
using static TorchSharp.torch;
namespace OnlyR.Core.Recorder
{
    /// <summary>
    /// The audio recorder. Uses NAudio for the heavy lifting, but it's isolated in this class
    /// so if we need to replace NAudio with another library we just need to modify this part
    /// of the application.
    /// </summary>
    public sealed class AudioRecorder : IDisposable
    {
        // use these 2 together. Experiment to get the best VU display...
        private const int RequiredReportingIntervalMs = 40;
        private const int VuSpeed = 5;
        private LameMP3FileWriter? _mp3Writer;
        private IWaveIn? _waveSource;
        private IWaveIn? _loopbackCapture;
        private IWaveIn? micCapture;
        private WaveOutEvent? _silenceWaveOut;
        private SampleAggregator? _sampleAggregator;
        private VolumeFader? _fader;
        private RecordingStatus _recordingStatus;
        private string? _tempRecordingFilePath;
        private string? _finalRecordingFilePath;
        private torch.jit.ScriptModule _model;
        private int _dampedLevel;
        private Overlay overlay;
        



        bool loopBack = false;
        //byte[]? combinedBuffer = null;
        public List<float> inferenceTime = new List<float>();
        public List<float> AccuracyMean = new List<float>();
        int i = 1;
        byte[] sample = new byte[128000];
        int _totalBytesAccumulated = 0;
        byte[] saveSample = new byte[128000];
        public AudioRecorder()
        {
            string assemblyLocation = Assembly.GetExecutingAssembly().Location;

            // 어셈블리의 디렉토리를 얻습니다.
            string assemblyPath = Path.GetDirectoryName(assemblyLocation);

            // 모델 파일의 이름입니다.
            string modelFileName = "W2V2BASE_AASISTL_DKDLoss_cnsl_audiomentations_5_best11_no_optimize_mobile.pt";

            // 모델 파일의 전체 경로를 구성합니다.
            string modelFilePath = Path.Combine(assemblyPath, modelFileName);
            _recordingStatus = RecordingStatus.NotRecording;
            _model = torch.jit.load(modelFilePath);
            Console.WriteLine("model Load OK");
            _model.eval();
            overlay = new Overlay();
            overlay.Show();
           


        }

        public event EventHandler<RecordingProgressEventArgs>? ProgressEvent;

        public event EventHandler<RecordingStatusChangeEventArgs>? RecordingStatusChangeEvent;

        /// <summary>
        /// Gets a list of Windows recording devices.
        /// </summary>
        /// <returns>Collection of devices.</returns>
        public static IEnumerable<RecordingDeviceInfo> GetRecordingDeviceList()
        {
            var result = new List<RecordingDeviceInfo>();

            var count = WaveIn.DeviceCount;
            for (var n = 0; n < count; ++n)
            {
                var caps = WaveIn.GetCapabilities(n);
                result.Add(new RecordingDeviceInfo(n, caps.ProductName));
            }

            return result;
        }

        public void Dispose()
        {
            Cleanup();
        }

        /// <summary>
        /// Starts recording.
        /// </summary>
        /// <param name="recordingConfig">Recording configuration.</param>
         bool RecordEach = false;
        public void Start(RecordingConfig recordingConfig)
        {
           string finalFolderPath = Path.GetDirectoryName(recordingConfig.FinalFilePath);

            if (_recordingStatus == RecordingStatus.NotRecording)
            {
                CheckRecordingDevice(recordingConfig);

                if (recordingConfig.UseLoopbackCapture)
                {
                    _waveSource = new WasapiLoopbackCapture()
                    {
                        WaveFormat = new WaveFormat(16000, 16, 1)
                    };
                    ConfigureSilenceOut();
                    RecordEach = true;
                    loopBack = true;
                }
                else if (recordingConfig.UseBoth)
                {   
                    byte[]? micBuffer = null;
                    byte[]? loopbackBuffer = null;
                    int micBytes = 0;
                    int loopbackBytes = 0;
                    Console.Write("use both");                
                    _mp3Writer = new LameMP3FileWriter(
                       recordingConfig.DestFilePath,
                       new WaveFormat(16000, 16, 1),
                       recordingConfig.Mp3BitRate,
                       CreateTag(recordingConfig));
                    RecordEach = false;
                    micCapture = new WasapiCapture
                    {
                        WaveFormat = new WaveFormat(16000, 16, 1),
                        //DeviceNumber = recordingConfig.RecordingDevice
                    };
                    var bufferedWaveProviderMic = new BufferedWaveProvider(new WaveFormat(16000, 16, 1));
                    // 룹백 캡처 인스턴스 생성 및 설정
                    _loopbackCapture = new WasapiLoopbackCapture
                    {
                        WaveFormat = new WaveFormat(16000, 16, 1)
                    };                  
                    ConfigureSilenceOut();
                    var bufferedWaveProviderLoopback = new BufferedWaveProvider(new WaveFormat(16000, 16, 1));
                    _loopbackCapture.DataAvailable += (s, e) =>
                    {                       
                        loopbackBuffer = e.Buffer;
                        loopbackBytes = e.BytesRecorded;
                        if (_fader != null && _fader.Active)
                        {
                            // we're fading out...
                            _fader.FadeBuffer(e.Buffer, e.BytesRecorded, false);
                        }
                        AddToSampleAggregator(e.Buffer, e.BytesRecorded, false);
                     
                        if (micBuffer != null && micBytes > 0)
                        {
                            micBuffer = null;
                            micBytes = 0;
                        }
                    };
                        _loopbackCapture.RecordingStopped += WaveSourceRecordingStoppedHandler;                  
                        InitAggregator(_loopbackCapture.WaveFormat.SampleRate);
                        InitFader(_loopbackCapture.WaveFormat.SampleRate);

                    micCapture.DataAvailable += (s, e) =>
                    {
                        micBuffer = e.Buffer;
                        micBytes = e.BytesRecorded;
                       
                        if (_fader != null && _fader.Active)
                        {
                            // we're fading out...
                            _fader.FadeBuffer(e.Buffer, e.BytesRecorded, false);
                        }
                        AddToSampleAggregator(e.Buffer, e.BytesRecorded, false);
                  
                        if (loopbackBuffer != null && loopbackBytes > 0)
                        {
                            byte[] combineByte = CombineAndWriteAudioData(loopbackBuffer, loopbackBytes, micBuffer, micBytes);
                            int shortsToCopy= Math.Min(micBytes, loopbackBytes); 
                            int expectedAccumulation = _totalBytesAccumulated + micBytes;
                            if (expectedAccumulation > 128000)
                            {
                                // 64000을 초과하지 않도록 누적할 데이터의 양 조절
                                shortsToCopy = 128000 - _totalBytesAccumulated;
                            }
                            Array.Copy(combineByte, 0, sample, _totalBytesAccumulated, shortsToCopy);
                            _totalBytesAccumulated += shortsToCopy;
                            if (_totalBytesAccumulated == 128000)
                            {
                                
                                    Inference(ConvertByteToFloat(sample), sample, finalFolderPath);
                                    Array.Clear(sample, 0, sample.Length);
                                    // 누적된 데이터와 카운터 초기화
                                    _totalBytesAccumulated = 0;
                             
                            }
                            loopbackBuffer =null;    
                            loopbackBytes = 0;
                            
                        }
                    };
                        micCapture.RecordingStopped += WaveSourceRecordingStoppedHandler;
                        InitAggregator(micCapture.WaveFormat.SampleRate);
                        InitFader(micCapture.WaveFormat.SampleRate);
                        //micCapture.StartRecording();
                        micCapture.StartRecording();
                        _loopbackCapture.StartRecording();

                        _tempRecordingFilePath = recordingConfig.DestFilePath;
                        _finalRecordingFilePath = recordingConfig.FinalFilePath;

                        OnRecordingStatusChangeEvent(new RecordingStatusChangeEventArgs(RecordingStatus.Recording)
                        {
                            TempRecordingPath = _tempRecordingFilePath,
                            FinalRecordingPath = _finalRecordingFilePath
                        });

                    }
                else
                {
                    _waveSource = new WaveIn
                    {
                        WaveFormat = new WaveFormat(recordingConfig.SampleRate, 16, recordingConfig.ChannelCount),
                        DeviceNumber = recordingConfig.RecordingDevice,
                    };
                    RecordEach = true;
                }


                if (RecordEach)
                {
                    InitAggregator(_waveSource.WaveFormat.SampleRate);
                    InitFader(_waveSource.WaveFormat.SampleRate);

                    _waveSource.DataAvailable += (s, waveInEventArgs) =>
                         {
                             // as audio samples are provided by WaveIn, we hook in here 
                             // and write them to disk, encoding to MP3 on the fly 
                             // using the _mp3Writer.
                             var buffer = waveInEventArgs.Buffer;
                             var bytesRecorded = waveInEventArgs.BytesRecorded;
                             var buff = new WaveBuffer(buffer);
                             int shortsToCopy = bytesRecorded;
                             int expectedAccumulation = _totalBytesAccumulated + bytesRecorded;
                             //string outputPath = Path.Combine(@"C:\Users\wsm04\Desktop\audio");
                             if (expectedAccumulation > 128000)
                             {
                                 // 64000을 초과하지 않도록 누적할 데이터의 양 조절
                                 shortsToCopy = 128000 - _totalBytesAccumulated;
                             }
                             Array.Copy(buff.ByteBuffer, 0, sample, _totalBytesAccumulated, shortsToCopy);
                             //Array.Copy(buff.ByteBuffer, 0, saveSample, _totalBytesAccumulated, bytesRecorded);
                             //SaveByteArrayAsWav(buffer, outputPath);
                             _totalBytesAccumulated += shortsToCopy;
                             //samplerate 16000 mono 면 
                             if (_totalBytesAccumulated == 128000)
                             {
                                 if (loopBack == true)
                                 {
                                     double average = sample.Average(b => (double)b);
                                     if ((average > 240 && average < 260) || CheckIf90PercentZeros(sample) == true)
                                     {
                                         Console.Write("Not Inference");
                                         overlay.UpdateOverlay("Not Inference");
                                         Array.Clear(sample, 0, sample.Length);
                                         _totalBytesAccumulated = 0;
                                     }
                                     else
                                     {
                                         Inference(ConvertByteToFloat(sample), sample, finalFolderPath);
                                         Array.Clear(sample, 0, sample.Length);
                                         // 누적된 데이터와 카운터 초기화
                                         _totalBytesAccumulated = 0;
                                     }
                                 }
                                 else
                                 {
                                     Inference(ConvertByteToFloat(sample), sample, finalFolderPath);
                                     Array.Clear(sample, 0, sample.Length);
                                     // 누적된 데이터와 카운터 초기화
                                     _totalBytesAccumulated = 0;
                                 }
                             }
                             var isFloatingPointAudio = _waveSource?.WaveFormat.BitsPerSample == 32;

                             if (_fader != null && _fader.Active)
                             {
                                 // we're fading out...
                                 _fader.FadeBuffer(buffer, bytesRecorded, isFloatingPointAudio);
                             }

                             AddToSampleAggregator(buffer, bytesRecorded, isFloatingPointAudio);

                             _mp3Writer?.Write(buffer, 0, bytesRecorded);
                         };
                    _waveSource.RecordingStopped += WaveSourceRecordingStoppedHandler;

                    _mp3Writer = new LameMP3FileWriter(
                        recordingConfig.DestFilePath,
                        _waveSource.WaveFormat,
                        recordingConfig.Mp3BitRate,
                        CreateTag(recordingConfig));

                    _waveSource.StartRecording();

                    _tempRecordingFilePath = recordingConfig.DestFilePath;
                    _finalRecordingFilePath = recordingConfig.FinalFilePath;

                    OnRecordingStatusChangeEvent(new RecordingStatusChangeEventArgs(RecordingStatus.Recording)
                    {
                        TempRecordingPath = _tempRecordingFilePath,
                        FinalRecordingPath = _finalRecordingFilePath
                    });
                }
            }

        }
   
        
        
        private byte[] CombineAndWriteAudioData( byte[] loopbackBuffer, int loopbackBytes,byte[] micBuffer, int micBytes)
        {           
            // 여기서는 두 버퍼의 크기가 같다고 가정합니다. 실제 상황에서는 버퍼 크기가 다를 수 있으므로, 처리 로직에 따라 조정이 필요할 수 있습니다.
            int bytesToProcess = Math.Min(micBytes, loopbackBytes);
            //byte[] combinedBuffer = new byte[3200];
            byte[] combinedBuffer = new byte[loopbackBuffer.Length];
            
            for (int i = 0; i < bytesToProcess; i += 2) // 16비트 오디오 샘플 처리를 가정
            {
                // 두 오디오 소스에서 샘플을 추출합니다.
                short micSample = BitConverter.ToInt16(micBuffer, i);
                short loopbackSample = BitConverter.ToInt16(loopbackBuffer, i);

                // 샘플을 혼합합니다. 오버플로우를 방지하기 위해 샘플을 평균화합니다.
                short mixedSample = (short)((micSample*2 + loopbackSample) / 2);

                // 혼합된 샘플을 결과 버퍼에 저장합니다.
                byte[] mixedSampleBytes = BitConverter.GetBytes(mixedSample);
                combinedBuffer[i] = mixedSampleBytes[0];
                combinedBuffer[i + 1] = mixedSampleBytes[1];
            }
            _mp3Writer?.Write(combinedBuffer, 0, bytesToProcess);
            //_mp3Writer?.Write(combinedBuffer, 0, combinedBuffer.Length);
            return combinedBuffer;
        }

       

        private void ConfigureSilenceOut()
        {
            // WasapiLoopbackCapture doesn't record any audio when nothing is playing
            // so we must play some silence!

            var silence = new SilenceProvider(new WaveFormat(16000,16, 1));
            _silenceWaveOut = new WaveOutEvent();
            _silenceWaveOut.Init(silence);
            _silenceWaveOut.Play();
        }
       
        /// <summary>
        /// Stop recording.
        /// </summary>
        /// <param name="fadeOut">true - fade out the recording instead of stopping immediately.</param>
        public void Stop(bool fadeOut)
        {
            if (_recordingStatus == RecordingStatus.Recording)
            {
                OnRecordingStatusChangeEvent(new RecordingStatusChangeEventArgs(RecordingStatus.StopRequested)
                {
                    TempRecordingPath = _tempRecordingFilePath,
                    FinalRecordingPath = _finalRecordingFilePath,
                });

                if (fadeOut)
                {
                    _fader?.Start();
                }
                else
                {
                    if (RecordEach) {
                        _waveSource?.StopRecording();
                    }
                    else
                    {
                        _loopbackCapture?.StopRecording();
                        micCapture?.StopRecording();
                    }
                    _silenceWaveOut?.Stop();
                }
            }
        }

        private static ID3TagData CreateTag(RecordingConfig recordingConfig)
        {
            // tag is embedded as MP3 metadata
            return new()
            {
                Title = recordingConfig.TrackTitle,
                Album = recordingConfig.AlbumName,
                Track = recordingConfig.TrackNumber.ToString(),
                Genre = recordingConfig.Genre,
                Year = recordingConfig.RecordingDate.Year.ToString(),
            };
        }

        private static void CheckRecordingDevice(RecordingConfig recordingConfig)
        {
            var deviceCount = WaveIn.DeviceCount;
            if (deviceCount == 0)
            {
                throw new NoDevicesException();
            }

            if (!recordingConfig.UseLoopbackCapture && recordingConfig.RecordingDevice >= deviceCount)
            {
                recordingConfig.RecordingDevice = 0;
            }
        }

        private void InitAggregator(int sampleRate)
        {
            // the aggregator collects audio sample metrics 
            // and publishes the results at suitable intervals.
            // Used by the OnlyR volume meter
            if (_sampleAggregator != null)
            {
                _sampleAggregator.ReportEvent -= AggregatorReportHandler;
            }

            _sampleAggregator = new SampleAggregator(sampleRate, RequiredReportingIntervalMs);
            _sampleAggregator.ReportEvent += AggregatorReportHandler;
        }

        private void AggregatorReportHandler(object? sender, SamplesReportEventArgs e)
        {
            var value = Math.Max(e.MaxSample, Math.Abs(e.MinSample)) * 100;

            var damped = GetDampedVolumeLevel(value);
            OnProgressEvent(new RecordingProgressEventArgs { VolumeLevelAsPercentage = damped });
        }
        public void Inference(float[] audioData, byte[] saveSample,string path)
        {
            
            //Console.Write(_totalBytesAccumulated + "2");
            long inputSize = audioData.Length;
        
            //Console.Write(inputSize);
            //var startTime = DateTime.Now;
            Tensor inTensor = torch.tensor(audioData, new long[] { inputSize });
        
            var startTime = DateTime.Now;
            Tensor score =(Tensor)_model.forward(inTensor);
            var endTime = DateTime.Now;
            float result = score.data<float>()[0];
            float showResult = result * 100; //결과를 float형태로 받아옴
            Double DoubleTime = (endTime - startTime).TotalMilliseconds;
            float FloatTime = (float)DoubleTime; //float형태의 추론시간
            AccuracyMean.Add(showResult);
            inferenceTime.Add(FloatTime); //추론시간을 list의 저장 나중에 평균 추론시간 구하기 위해서
            string formattedResult = showResult.ToString("F2"); //UI에 보이는 값을 편리하게 보기위해 소수 둘째점으로 제한
            Console.WriteLine($"Inference Time: {DoubleTime} ms"); //추론시간 출력
            Console.WriteLine(formattedResult); //추론 결과 출력
                                                //Console.WriteLine("success");
            string fileName = $"{i}_{showResult}.wav";
            //string fileName = $"oldModel_screen_Media_segment_{i}_{showResult}.wav";
            string outputPath = Path.Combine(path, fileName); // savepath
            SaveByteArrayAsWav(saveSample, outputPath); //모델 성능 비교위해 만듬
            overlay.UpdateOverlay($"{formattedResult}"); //UI에 표시
                i = i + 1;
        }
        public float[] ConvertByteToFloat(byte[] shortData)
        {
            float[] floatData= new float[shortData.Length / 2];
            for (int i = 0; i < shortData.Length/2; i++)
            {
                // 바이트 배열에서 16비트 정수로 변환 (Little Endian)
                int index = i * 2; 
                short sample = (short)((shortData[index] & 0xff) | (shortData[index + 1] << 8));
                
                // 16비트 정수를 -1.0과 1.0 사이의 float 값으로 스케일링
                floatData[i] = (float)sample / 32768.0f; // short.MaxValue + 1.0f 로 나누어줌
            }
            return floatData;
        }
        private void WaveSourceRecordingStoppedHandler(object? sender, StoppedEventArgs e)
        {
            if (inferenceTime.Count > 0)
            {
                float average = inferenceTime.Average();
                float Aaverage= AccuracyMean.Average();
                Console.WriteLine($"평균 추론 시간: {average}ms");
                Console.WriteLine($"평균 정확도: {Aaverage}%");
                inferenceTime.Clear();  
                AccuracyMean.Clear();
            }
            
            _totalBytesAccumulated = 0;
            // sample 배열 초기화
            Array.Clear(sample, 0, sample.Length);
            i = 1;
            Cleanup();
            OnRecordingStatusChangeEvent(new RecordingStatusChangeEventArgs(RecordingStatus.NotRecording));
            _fader = null;
        }

        private void LoopBackRecordingStoppedHandler(object? sender, StoppedEventArgs e)
        {
            if (inferenceTime.Count > 0)
            {
                float average = inferenceTime.Average();
                Console.WriteLine($"평균 추론 시간: {average}ms");
            }

            _totalBytesAccumulated = 0;
            // sample 배열 초기화
            Array.Clear(sample, 0, sample.Length);
            i = 1;
            Cleanup();
            OnRecordingStatusChangeEvent(new RecordingStatusChangeEventArgs(RecordingStatus.NotRecording));
            _fader = null;
        }


        //Data핸들러
       /* private void WaveSourceDataAvailableHandler(object? sender, WaveInEventArgs waveInEventArgs)
        {
            // as audio samples are provided by WaveIn, we hook in here 
            // and write them to disk, encoding to MP3 on the fly 
            // using the _mp3Writer.
            var buffer = waveInEventArgs.Buffer;
            var bytesRecorded = waveInEventArgs.BytesRecorded;
            var buff = new WaveBuffer(buffer);
            int shortsToCopy = bytesRecorded ;
            int expectedAccumulation = _totalBytesAccumulated + bytesRecorded ;
            //string outputPath = Path.Combine(@"C:\Users\wsm04\Desktop\audio");
            if (expectedAccumulation > 128000)
            {
                // 64000을 초과하지 않도록 누적할 데이터의 양 조절
                shortsToCopy = 128000 - _totalBytesAccumulated;
            }
            Array.Copy(buff.ByteBuffer, 0, sample, _totalBytesAccumulated, shortsToCopy);
            //Array.Copy(buff.ByteBuffer, 0, saveSample, _totalBytesAccumulated, bytesRecorded);
            //SaveByteArrayAsWav(buffer, outputPath);
            _totalBytesAccumulated += shortsToCopy;
            //samplerate 16000 mono 면 
            if (_totalBytesAccumulated == 128000)
            {
                if (loopBack==true)
                {
                    double average = sample.Average(b => (double)b);
                    if ((average > 240 && average < 260) || CheckIf90PercentZeros(sample)==true)
                    {
                        Console.Write("Not Inference");
                        overlay.UpdateOverlay("Not Inference");
                        Array.Clear(sample, 0, sample.Length);
                        _totalBytesAccumulated = 0;
                    }
                    else
                    {                        
                        Inference(ConvertByteToFloat(sample), sample);
                        Array.Clear(sample, 0, sample.Length);
                        // 누적된 데이터와 카운터 초기화
                        _totalBytesAccumulated = 0;
                    }
                }                        
                else
                {                    
                        Inference(ConvertByteToFloat(sample), sample);
                        Array.Clear(sample, 0, sample.Length);
                        // 누적된 데이터와 카운터 초기화
                        _totalBytesAccumulated = 0;
                }
            }
            var isFloatingPointAudio = _waveSource?.WaveFormat.BitsPerSample == 32;

            if (_fader != null && _fader.Active)
            {
                // we're fading out...
                _fader.FadeBuffer(buffer, bytesRecorded, isFloatingPointAudio);
            }

            AddToSampleAggregator(buffer, bytesRecorded, isFloatingPointAudio);

            _mp3Writer?.Write(buffer, 0, bytesRecorded);
        }*/
        private bool CheckIf90PercentZeros(byte[] buffer)
        {
            if (buffer == null || buffer.Length == 0)
            {
                throw new ArgumentException("Buffer is null or empty.");
            }

            int zeroCount = 0;
            foreach (var b in buffer)
            {
                if (b == 0)
                {
                    zeroCount++;
                }
            }

            double zeroPercentage = (double)zeroCount / buffer.Length * 100;
            return zeroPercentage >= 50;
        }

        private void AddToSampleAggregator(byte[] buffer, int bytesRecorded, bool isFloatingPointAudio)
        {
            var buff = new WaveBuffer(buffer);

            if (isFloatingPointAudio)
            {
                for (var index = 0; index < bytesRecorded / 4; ++index)
                {
                    var sample = buff.FloatBuffer[index];
                    _sampleAggregator?.Add(sample);
                }
            }
            else
            {
                for (var index = 0; index < bytesRecorded / 2; ++index)
                {
                    var sample = buff.ShortBuffer[index];
                    _sampleAggregator?.Add(sample / 32768F);
                }
            }
        }

        private void OnRecordingStatusChangeEvent(RecordingStatusChangeEventArgs e)
        {
            _recordingStatus = e.RecordingStatus;
            RecordingStatusChangeEvent?.Invoke(this, e);
        }

        private void OnProgressEvent(RecordingProgressEventArgs e)
        {
            ProgressEvent?.Invoke(this, e);
        }

        private int GetDampedVolumeLevel(float volLevel)
        {
            // provide some "damping" of the volume meter.
            if (volLevel > _dampedLevel)
            {
                _dampedLevel = (int)(volLevel + VuSpeed);
            }

            _dampedLevel -= VuSpeed;
            if (_dampedLevel < 0)
            {
                _dampedLevel = 0;
            }

            return _dampedLevel;
        }

        private void FadeCompleteHandler(object? sender, System.EventArgs e)
        {

            if (RecordEach)
            {
                _waveSource?.StopRecording();
            }
            else
            {
                _loopbackCapture?.StopRecording();
                micCapture?.StopRecording();
                
            }
            _silenceWaveOut?.Stop();
        }

        private void Cleanup()
        {
            _mp3Writer?.Flush();

            if (RecordEach)
            {
                _waveSource?.Dispose();
                _waveSource = null;
            }
            else
            {
                _loopbackCapture?.Dispose();
                _loopbackCapture = null;
                micCapture?.Dispose();
                micCapture = null;
           
            }

            _silenceWaveOut?.Dispose();
            _silenceWaveOut = null;

            _mp3Writer?.Dispose();
            _mp3Writer = null;

            _tempRecordingFilePath = null;
        }

        private void InitFader(int sampleRate)
        {
            // used to optionally fade out a recording
            _fader = new VolumeFader(sampleRate);
            _fader.FadeComplete += FadeCompleteHandler;
        }
        public void SaveFloatArrayAsWav(float[] audioData, int sampleRate, string outputPath)
        {
            // Number of channels is 1 as we're assuming mono audio
            int channels = 1;

            // Create a WaveFormat instance for the output file
            WaveFormat waveFormat = WaveFormat.CreateIeeeFloatWaveFormat(sampleRate, channels);

            // Use the WaveFileWriter class to write the audio data to a .wav file
            using (var writer = new WaveFileWriter(outputPath, waveFormat))
            {
                // Convert the float array to byte array
                byte[] byteArray = new byte[audioData.Length*2]; // 4 bytes per float
                Buffer.BlockCopy(audioData, 0, byteArray, 0, byteArray.Length);

                // Write the byte array to the .wav file
                writer.Write(byteArray, 0, byteArray.Length);
            }
        }
        public void SaveByteArrayAsWav(byte[] audioData, string outputPath)
        {
            int sampleRate = 16000;
            int bitsPerSample =16 ;
            int channels = 1;
            // WaveFormat 생성
            WaveFormat waveFormat = new WaveFormat(sampleRate, bitsPerSample, channels);

            // WaveFileWriter를 사용하여 오디오 데이터를 .wav 파일로 쓰기
            using (var writer = new WaveFileWriter(outputPath, waveFormat))
            {
                writer.Write(audioData, 0, audioData.Length);
            }
        }
        //팝업알림창
        private void ShowNotification(string text, string title)
        {
            NotifyIcon notifyIcon = new NotifyIcon
            {
                Visible = true,
                Icon = SystemIcons.Information,
                BalloonTipIcon = ToolTipIcon.Info,
                BalloonTipTitle = title,
                BalloonTipText = text
            };

            notifyIcon.ShowBalloonTip(1000); // 1초 동안 보여줌

            // 알림이 사라진 후 아이콘도 숨김
            notifyIcon.Dispose();
        }
       

    }
}
