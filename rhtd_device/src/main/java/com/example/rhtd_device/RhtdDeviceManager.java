package com.example.rhtd_device;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android_serialport_api.Device;
import android_serialport_api.SerialPortManager;

public class RhtdDeviceManager {
    private static RhtdDeviceManager instance;
    private Handler mHfCardDeviceHandler;
    private Handler mIcCardHandler;
    private Handler mLockDeviceHandler;
    private SerialPortManager hfCardManager;
    private SerialPortManager icCardManager;
    private SerialPortManager lockDeviceManager;
    private Callback listener;
    private Map<Integer, String[]> hfCardsMap = new HashMap<>();
    private Map<Integer, String> hfCardDoorStateMap = new HashMap<>();
    private Map<Integer, String[]> lockDeviceDoorStateMap = new HashMap<>();
    private int hfMode = 0;
    /**
     * FG高频模块-指令
     */
    //数据头
    private String hexHead = "AA66";
    //全部初始化为蓝灯
    private String lampBlueString = "0A%sAB00000000000000";
    //指定亮红灯
    private String lampRedString = "0A%sAB%s%s%s%s000000";
    //全开电机
    private String openAllMotorString = "0A%sA3FFFFFFFF000000";
    //全关电机
    private String closeAllMotorString = "0A%sA2FFFFFFFF000000";
    //开指定电机
    private String openMotorString = "0A%sA3%s%s%s%s000000";
    //关指定电机
    private String closeMotorString = "0A%sA2%s%s%s%s000000";
    /**
     * XG高频模块-指令
     */
    //数据头
    private String hexHeadXg = "A0";
    //查询卡号
    private String mSearchCard = "%s0100000000";
    //查询门锁状态
    private String mSearchDoorState = "%s0B00000000";
    //控灯闪烁
    private String mSetLampFlashing = "%s18%s%s%s%s";
    //控灯停止闪烁
    private String mSetLampDefault = "%s17%s%s%s%s";
    //开指定电机
    private String mOpenMotor = "%s16%s%s%s%s";
    //关指定电机
    private String mCloseMotor = "%s15%s%s%s%s";
    //全开电机
    private String mOpenAllMotor = "%s0F01000000";
    //全关电机
    private String mCloseAllMotor = "%s0A01000000";
    //打开变化上送设置
    private String openChangeReception = "%s0300000100";
    //关闭变化上送设置
    private String closeChangeReception = "%s0300000000";
    /**
     * XG锁板-指令
     */
    //数据头
    private String lockCommandHeadString = "A0";
    //开锁指令
    private String openLockString = "%s16%s%s%s%s";
    //读取锁反馈指令
    private String readerLockStateString = "%s1400000000";

    public static synchronized RhtdDeviceManager getInstance() {
        if (instance == null) {
            instance = new RhtdDeviceManager();
        }
        return instance;
    }

    /**
     * 高频模块连接
     *
     * @param device 串口号地址
     * @param mode   模块类型 0-FG 1-XG
     * @return
     */
    public boolean connectHfCard(String device, int mode) {
        hfMode = mode;
        final Device mDevice = new Device();
        mDevice.path = device;
        mDevice.speed = 115200;
        mDevice.dataBits = 8;
        mDevice.stopBits = 1;
        //校验位, 'n': 无校验 'e': 偶校验 'o': 奇校验
        mDevice.parity = 'n';
        hfCardManager = new SerialPortManager(mDevice);
        hfCardManager.setOnDataReceiveListener(new SerialPortManager.OnDataReceiveListener() {
            @Override
            public void onDataReceive(byte[] recvBytes, int a) {
                if (recvBytes != null && recvBytes.length > 0) {
                    if (recvBytes.length == 139) {
                        String receptionData = bytesToHexString(recvBytes, recvBytes.length);
                        if (listener != null) listener.onHfCardReception(receptionData);
                        if (hfMode == 0) {
                            if (TextUtils.equals(receptionData.substring(0, 8), "AA123456") && checkHexStringIsEquals(makeChecksum(receptionData.substring(8, 276)), receptionData.substring(276, 278))) {
                                int loc;
                                if (TextUtils.equals(receptionData.substring(8, 10), "A0")) {
                                    loc = 1;
                                } else if (TextUtils.equals(receptionData.substring(8, 10), "A4")) {
                                    loc = 1;
                                } else if (TextUtils.equals(receptionData.substring(8, 10), "88")) {
                                    loc = 1;
                                } else if (TextUtils.equals(receptionData.substring(8, 10), "B2")) {
                                    loc = 1;
                                } else {
                                    loc = Integer.parseInt(receptionData.substring(9, 10), 16);
                                }
                                if (TextUtils.equals(receptionData.substring(12, 14), "00")) {
                                    hfCardDoorStateMap.put(loc, "0");
                                } else {
                                    hfCardDoorStateMap.put(loc, "1");
                                }
                                int count = 32;
                                String data = receptionData.substring(20, count * 8 + 20);
                                String[] cards = splitString(data, data.length());
                                hfCardsMap.put(loc, cards);
                                if (listener != null)
                                    listener.onHfCardReceptionDoorStates(hfCardDoorStateMap);
                                if (listener != null) listener.onHfCardReceptionCards(hfCardsMap);
                            }
                        } else {
                            if (receptionData.substring(0, 2).equals("CC")) {
                                int loc = Integer.parseInt(receptionData.substring(2, 4), 16);
                                //模块数量
                                int count = Integer.parseInt(receptionData.substring(4, 6), 16);
                                //模块卡号数据
                                String data = receptionData.substring(10, count * 22 + 6);
                                String[] cards = data.split("AA00");
                                hfCardsMap.put(loc, cards);
                                if (listener != null) listener.onHfCardReceptionCards(hfCardsMap);
                            } else if (receptionData.substring(0, 2).equals("B0")) {
                                int loc = Integer.parseInt(receptionData.substring(2, 4), 16);
                                if (TextUtils.equals(receptionData.substring(4, 6), "0B")) {
                                    if (TextUtils.equals(receptionData.substring(10, 12), "00")) {
                                        hfCardDoorStateMap.put(loc, "0");
                                    } else {
                                        hfCardDoorStateMap.put(loc, "1");
                                    }
                                    if (listener != null)
                                        listener.onHfCardReceptionDoorStates(hfCardDoorStateMap);
                                }
                            }
                        }
                    }
                }
            }
        });
        return true;
    }

    /**
     * 刷卡器模块连接
     *
     * @param device 串口号地址
     * @return
     */
    public boolean connectIcCard(String device) {
        final Device mDevice = new Device();
        mDevice.path = device;
        mDevice.speed = 9600;
        mDevice.dataBits = 8;
        mDevice.stopBits = 1;
        //校验位, 'n': 无校验 'e': 偶校验 'o': 奇校验
        mDevice.parity = 'n';
        icCardManager = new SerialPortManager(mDevice);
        icCardManager.setOnDataReceiveListener(new SerialPortManager.OnDataReceiveListener() {
            @Override
            public void onDataReceive(byte[] recvBytes, int i) {
                if (recvBytes != null && recvBytes.length > 0) {
                    String receptionData = bytesToHexString(recvBytes, recvBytes.length);
                    if (listener != null) listener.onIdCardReception(receptionData);
                }
            }
        });
        return true;
    }

    /**
     * 锁控模块连接
     *
     * @param device 串口号地址
     * @return
     */
    public boolean connectLockDevice(String device) {
        final Device mDevice = new Device();
        mDevice.path = device;
        mDevice.speed = 9600;
        mDevice.dataBits = 8;
        mDevice.stopBits = 1;
        //校验位, 'n': 无校验 'e': 偶校验 'o': 奇校验
        mDevice.parity = 'n';
        lockDeviceManager = new SerialPortManager(mDevice);
        lockDeviceManager.setOnDataReceiveListener(new SerialPortManager.OnDataReceiveListener() {
            @Override
            public void onDataReceive(byte[] recvBytes, int a) {
                if (recvBytes != null && recvBytes.length > 0) {
                    String receptionData = bytesToHexString(recvBytes, recvBytes.length);
                    if (listener != null) listener.onLockDeviceReception(receptionData);
                    if (TextUtils.equals(receptionData.substring(0, 2), "B0") && TextUtils.equals(receptionData.substring(4, 6), "14")) {
                        String address = receptionData.substring(2, 4);
                        String substring = receptionData.substring(6, receptionData.length() - 2);
                        String m_lockState = hexString2binaryString(substring);
                        String state[] = new String[m_lockState.length()];
                        for (int i = 0; i < m_lockState.length(); i++) {
                            state[i] = String.valueOf(m_lockState.charAt(i));
                        }
                        lockDeviceDoorStateMap.put(Integer.parseInt(address), state);
                        if (listener != null)
                            listener.onLockDeviceReceptionDoorStates(lockDeviceDoorStateMap);
                    }
                }
            }
        });
        return true;
    }

    /**
     * 串口资源释放
     *
     * @return
     */
    public void closeSerialPort() {
        if (mHfCardDeviceHandler != null) {
            mHfCardDeviceHandler.removeCallbacksAndMessages(null); // 清除所有回调和消息
            mHfCardDeviceHandler.getLooper().quit(); // 安全退出Looper循环
        }
        if (mIcCardHandler != null) {
            mIcCardHandler.removeCallbacksAndMessages(null); // 清除所有回调和消息
            mIcCardHandler.getLooper().quit(); // 安全退出Looper循环
        }
        if (mLockDeviceHandler != null) {
            mLockDeviceHandler.removeCallbacksAndMessages(null); // 清除所有回调和消息
            mLockDeviceHandler.getLooper().quit(); // 安全退出Looper循环
        }
        listener = null;
        hfCardManager.closeSerialPort();
        icCardManager.closeSerialPort();
        lockDeviceManager.closeSerialPort();
    }

    /**
     * 查询锁开关状态
     *
     * @param moduleId 锁控模块地址
     * @return
     */
    public void readLockState(final int moduleId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mLockDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                mLockDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String data = String.format(readerLockStateString, integerToHexString(moduleId));
                        String crcXor = makeChecksum(data);
                        if (listener != null)
                            listener.onLockDeviceSend(lockCommandHeadString + data + crcXor);
                        lockDeviceManager.sendPacket(hexString2Bytes(lockCommandHeadString + data + crcXor));
                    }
                }, 100 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 查询锁开关状态
     *
     * @param moduleIdList 多个锁控模块地址集合
     * @return
     */
    public void readLockState(final List<Integer> moduleIdList) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mLockDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int moduleId : moduleIdList) {
                    mLockDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String data = String.format(readerLockStateString, integerToHexString(moduleId));
                            String crcXor = makeChecksum(data);
                            if (listener != null)
                                listener.onLockDeviceSend(lockCommandHeadString + data + crcXor);
                            lockDeviceManager.sendPacket(hexString2Bytes(lockCommandHeadString + data + crcXor));
                        }
                    }, 100 * time); // 延时时间逐步增加
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 锁控模块开锁
     *
     * @param openMap Map<锁控模块地址,开锁位置集合>
     * @return
     */
    public void openDoor(final Map<Integer, List<Integer>> openMap) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mLockDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int i : openMap.keySet()) {
                    final int finalI = i;
                    final List<Integer> lockList = openMap.get(i);
                    int m, n, l, o;
                    m = n = l = o = 0;
                    for (int s : lockList) {
                        if (s >= 9 && s <= 16) {
                            int a = s - 9;
                            m += (0x01 << a);
                        } else if (s >= 17 && s <= 24) {
                            int a = s - 17;
                            n += (0x01 << a);
                        } else if (s >= 25 && s <= 32) {
                            int a = s - 25;
                            o += (0x01 << a);
                        } else if (s >= 1 && s <= 8) {
                            int a = s - 1;
                            l += (0x01 << a);
                        }
                    }
                    int finalL = l;
                    int finalM = m;
                    int finalN = n;
                    int finalO = o;
                    mLockDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String data = String.format(openLockString, integerToHexString(finalI), integerToHexString(finalL), integerToHexString(finalM), integerToHexString(finalN), integerToHexString(finalO));
                            String crcXor = makeChecksum(data);
                            if (listener != null)
                                listener.onLockDeviceSend(lockCommandHeadString + data + crcXor);
                            lockDeviceManager.sendPacket(hexString2Bytes(lockCommandHeadString + data + crcXor));
                        }
                    }, 100 * time); // 延时时间逐步增加
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 锁控模块开锁
     *
     * @param moduleId 锁控模块地址
     * @param location 开锁位置集合
     * @return
     */
    public void openDoor(final int moduleId, final int location) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mLockDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                int m, n, l, o;
                m = n = l = o = 0;
                if (location >= 9 && location <= 16) {
                    int a = location - 9;
                    m += (0x01 << a);
                } else if (location >= 17 && location <= 24) {
                    int a = location - 17;
                    n += (0x01 << a);
                } else if (location >= 25 && location <= 32) {
                    int a = location - 25;
                    o += (0x01 << a);
                } else if (location >= 1 && location <= 8) {
                    int a = location - 1;
                    l += (0x01 << a);
                }
                int finalL = l;
                int finalM = m;
                int finalN = n;
                int finalO = o;
                mLockDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String data = String.format(openLockString, integerToHexString(moduleId), integerToHexString(finalL), integerToHexString(finalM), integerToHexString(finalN), integerToHexString(finalO));
                        String crcXor = makeChecksum(data);
                        if (listener != null)
                            listener.onLockDeviceSend(lockCommandHeadString + data + crcXor);
                        lockDeviceManager.sendPacket(hexString2Bytes(lockCommandHeadString + data + crcXor));
                    }
                }, 100 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块开电机
     *
     * @param openMap Map<高频模块地址,开电机位置集合>
     * @return
     */
    public void openMotor(final Map<Integer, List<Integer>> openMap) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int moduleId : openMap.keySet()) {
                    int m, n, l, s;
                    m = n = l = s = 0;
                    for (int location : openMap.get(moduleId)) {
                        if (location == 0) {
                            location = 32;
                        }
                        if (location >= 9 && location <= 16) {
                            int a = location - 9;
                            m += (0x01 << a);
                        } else if (location >= 17 && location <= 24) {
                            int a = location - 17;
                            n += (0x01 << a);
                        } else if (location >= 25 && location <= 32) {
                            int a = location - 25;
                            s += (0x01 << a);
                        } else if (location >= 1 && location <= 8) {
                            int a = location - 1;
                            l += (0x01 << a);
                        }
                    }
                    int finalL = l;
                    int finalM = m;
                    int finalN = n;
                    int finalS = s;
                    mHfCardDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (hfMode == 0) {
                                String data = String.format(openMotorString, integerToHexStringToAA(moduleId), integerToHexStringToAA(finalL), integerToHexStringToAA(finalM), integerToHexStringToAA(finalN), integerToHexStringToAA(finalS));
                                String crcXor = makeChecksum(data);
                                if (listener != null)
                                    listener.onHfCardSend(hexHead + data + crcXor);
                                hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                            } else {
                                String data = String.format(mOpenMotor, integerToHexString(moduleId), integerToHexString(finalL), integerToHexString(finalM), integerToHexString(finalN), integerToHexString(finalS));
                                String crcXor = makeChecksum(data);
                                if (listener != null)
                                    listener.onHfCardSend(hexHeadXg + data + crcXor);
                                hfCardManager.sendPacket(hexString2Bytes(hexHeadXg + data + crcXor));
                            }
                        }
                    }, 130 * time); // 延时时间逐步增加
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块开电机
     *
     * @param moduleId 高频模块地址
     * @param location 开电机位置集合
     * @return
     */
    public void openMotor(final int moduleId, final int location) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                int m, n, l, o;
                m = n = l = o = 0;
                if (location >= 9 && location <= 16) {
                    int a = location - 9;
                    m += (0x01 << a);
                } else if (location >= 17 && location <= 24) {
                    int a = location - 17;
                    n += (0x01 << a);
                } else if (location >= 25 && location <= 32) {
                    int a = location - 25;
                    o += (0x01 << a);
                } else if (location >= 1 && location <= 8) {
                    int a = location - 1;
                    l += (0x01 << a);
                }
                int finalL = l;
                int finalM = m;
                int finalN = n;
                int finalO = o;
                mHfCardDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (hfMode == 0) {
                            String data = String.format(openMotorString, integerToHexString(moduleId), integerToHexString(finalL), integerToHexString(finalM), integerToHexString(finalN), integerToHexString(finalO));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHead + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                        } else {
                            String data = String.format(mOpenMotor, integerToHexString(moduleId), integerToHexString(finalL), integerToHexString(finalM), integerToHexString(finalN), integerToHexString(finalO));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHeadXg + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHeadXg + data + crcXor));
                        }
                    }
                }, 100 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块关电机
     *
     * @param openMap Map<高频模块地址,关电机位置集合>
     * @return
     */
    public void closeMotor(final Map<Integer, List<Integer>> openMap) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int key : openMap.keySet()) {
                    int m, n, l, s;
                    m = n = l = s = 0;
                    for (int location : openMap.get(key)) {
                        if (location == 0) {
                            location = 32;
                        }
                        if (location >= 9 && location <= 16) {
                            int a = location - 9;
                            m += (0x01 << a);
                        } else if (location >= 17 && location <= 24) {
                            int a = location - 17;
                            n += (0x01 << a);
                        } else if (location >= 25 && location <= 32) {
                            int a = location - 25;
                            s += (0x01 << a);
                        } else if (location >= 1 && location <= 8) {
                            int a = location - 1;
                            l += (0x01 << a);
                        }
                    }
                    int finalL = l;
                    int finalM = m;
                    int finalN = n;
                    int finalS = s;
                    mHfCardDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (hfMode == 0) {
                                String data = String.format(closeMotorString, integerToHexStringToAA(key), integerToHexStringToAA(finalL), integerToHexStringToAA(finalM), integerToHexStringToAA(finalN), integerToHexStringToAA(finalS));
                                String crcXor = makeChecksum(data);
                                if (listener != null)
                                    listener.onHfCardSend(hexHead + data + crcXor);
                                hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                            } else {
                                String data = String.format(mCloseMotor, integerToHexStringToAA(key), integerToHexStringToAA(finalL), integerToHexStringToAA(finalM), integerToHexStringToAA(finalN), integerToHexStringToAA(finalS));
                                String crcXor = makeChecksum(data);
                                if (listener != null)
                                    listener.onHfCardSend(hexHeadXg + data + crcXor);
                                hfCardManager.sendPacket(hexString2Bytes(hexHeadXg + data + crcXor));
                            }
                        }
                    }, 130 * time); // 延时时间逐步增加
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块关电机
     *
     * @param moduleId 高频模块地址
     * @param location 关电机位置集合
     * @return
     */
    public void closeMotor(final int moduleId, final int location) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                int m, n, l, o;
                m = n = l = o = 0;
                if (location >= 9 && location <= 16) {
                    int a = location - 9;
                    m += (0x01 << a);
                } else if (location >= 17 && location <= 24) {
                    int a = location - 17;
                    n += (0x01 << a);
                } else if (location >= 25 && location <= 32) {
                    int a = location - 25;
                    o += (0x01 << a);
                } else if (location >= 1 && location <= 8) {
                    int a = location - 1;
                    l += (0x01 << a);
                }
                int finalL = l;
                int finalM = m;
                int finalN = n;
                int finalO = o;
                mHfCardDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (hfMode == 0) {
                            String data = String.format(closeMotorString, integerToHexString(moduleId), integerToHexString(finalL), integerToHexString(finalM), integerToHexString(finalN), integerToHexString(finalO));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHead + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                        } else {
                            String data = String.format(mCloseMotor, integerToHexString(moduleId), integerToHexString(finalL), integerToHexString(finalM), integerToHexString(finalN), integerToHexString(finalO));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHeadXg + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHeadXg + data + crcXor));
                        }
                    }
                }, 100 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块全部初始化为蓝灯 仅为FG型号可用
     *
     * @param moduleIdList 高频模块地址集合
     * @return
     */
    public void setLampBlue(final List<Integer> moduleIdList) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int key : moduleIdList) {
                    mHfCardDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String data = String.format(lampBlueString, integerToHexStringToAA(key));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHead + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                        }
                    }, 130 * time);
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块全部初始化为蓝灯 仅为FG型号可用
     *
     * @param moduleId 高频模块地址
     * @return
     */
    public void setLampBlue(final int moduleId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                mHfCardDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String data = String.format(lampBlueString, integerToHexStringToAA(moduleId));
                        String crcXor = makeChecksum(data);
                        if (listener != null) listener.onHfCardSend(hexHead + data + crcXor);
                        hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                    }
                }, 130 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块全部初始化为蓝灯 仅为FG型号可用
     *
     * @param openMap Map<高频模块地址,变红灯位置集合>
     * @return
     */
    public void setLampRed(final Map<Integer, List<Integer>> openMap) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int key : openMap.keySet()) {
                    int m, n, l, s;
                    m = n = l = s = 0;
                    for (int location : openMap.get(key)) {
                        if (location == 0) {
                            location = 32;
                        }
                        if (location >= 9 && location <= 16) {
                            int a = location - 9;
                            m += (0x01 << a);
                        } else if (location >= 17 && location <= 24) {
                            int a = location - 17;
                            n += (0x01 << a);
                        } else if (location >= 25 && location <= 32) {
                            int a = location - 25;
                            s += (0x01 << a);
                        } else if (location >= 1 && location <= 8) {
                            int a = location - 1;
                            l += (0x01 << a);
                        }
                    }
                    int finalL = l;
                    int finalM = m;
                    int finalN = n;
                    int finalS = s;
                    mHfCardDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String data = String.format(lampRedString, integerToHexStringToAA(key), integerToHexStringToAA(finalL), integerToHexStringToAA(finalM), integerToHexStringToAA(finalN), integerToHexStringToAA(finalS));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHead + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                        }
                    }, 130 * time);
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块全部初始化为蓝灯 仅为FG型号可用
     *
     * @param moduleId 高频模块地址
     * @param location 变红灯位置
     * @return
     */
    public void setLampRed(final int moduleId, final int location) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                int m, n, l, o;
                m = n = l = o = 0;
                if (location >= 9 && location <= 16) {
                    int a = location - 9;
                    m += (0x01 << a);
                } else if (location >= 17 && location <= 24) {
                    int a = location - 17;
                    n += (0x01 << a);
                } else if (location >= 25 && location <= 32) {
                    int a = location - 25;
                    o += (0x01 << a);
                } else if (location >= 1 && location <= 8) {
                    int a = location - 1;
                    l += (0x01 << a);
                }
                int finalL = l;
                int finalM = m;
                int finalN = n;
                int finalO = o;
                mHfCardDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String data = String.format(lampRedString, integerToHexString(moduleId), integerToHexString(finalL), integerToHexString(finalM), integerToHexString(finalN), integerToHexString(finalO));
                        String crcXor = makeChecksum(data);
                        if (listener != null) listener.onHfCardSend(hexHead + data + crcXor);
                        hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                    }
                }, 130 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块查询卡号 仅为XG型号可用
     *
     * @param moduleId 高频模块地址
     * @return
     */
    public void hfSearchCard(int moduleId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                mHfCardDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String data = String.format(mSearchCard, integerToHexString(moduleId));
                        String crcXor = makeChecksum(data);
                        if (listener != null) listener.onLockDeviceSend(data + crcXor);
                        hfCardManager.sendPacket(hexString2Bytes(data + crcXor));
                    }
                }, 100 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块查询卡号 仅为XG型号可用
     *
     * @param moduleIdList 高频模块地址集合
     * @return
     */
    public void hfSearchCard(List<Integer> moduleIdList) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int moduleId : moduleIdList) {
                    mHfCardDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String data = String.format(mSearchCard, integerToHexStringToAA(moduleId));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHead + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                        }
                    }, 130 * time);
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块卡号数据变化上送 仅为XG型号可用
     *
     * @param moduleId 高频模块地址
     * @return
     */
    public void openChangeReceptionData(int moduleId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                mHfCardDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String data = String.format(openChangeReception, integerToHexString(moduleId));
                        String crcXor = makeChecksum(data);
                        if (listener != null) listener.onLockDeviceSend(data + crcXor);
                        hfCardManager.sendPacket(hexString2Bytes(data + crcXor));
                    }
                }, 100 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块卡号数据开启变化上送 仅为XG型号可用
     *
     * @param moduleIdList 高频模块地址集合
     * @return
     */
    public void openChangeReceptionData(List<Integer> moduleIdList) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int moduleId : moduleIdList) {
                    mHfCardDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String data = String.format(openChangeReception, integerToHexStringToAA(moduleId));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHead + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                        }
                    }, 130 * time);
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块卡号数据关闭变化上送 仅为XG型号可用
     *
     * @param moduleId 高频模块地址
     * @return
     */
    public void closeChangeReceptionData(int moduleId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                mHfCardDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String data = String.format(closeChangeReception, integerToHexString(moduleId));
                        String crcXor = makeChecksum(data);
                        if (listener != null) listener.onLockDeviceSend(data + crcXor);
                        hfCardManager.sendPacket(hexString2Bytes(data + crcXor));
                    }
                }, 100 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块卡号数据关闭变化上送 仅为XG型号可用
     *
     * @param moduleIdList 高频模块地址集合
     * @return
     */
    public void closeChangeReceptionData(List<Integer> moduleIdList) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int moduleId : moduleIdList) {
                    mHfCardDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String data = String.format(closeChangeReception, integerToHexStringToAA(moduleId));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHead + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                        }
                    }, 130 * time);
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块指定位置灯闪烁 仅为XG型号可用
     *
     * @param moduleId 高频模块地址
     * @param location 闪烁位置
     * @return
     */
    public void setLampFlashing(final int moduleId, final int location) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                int m, n, l, o;
                m = n = l = o = 0;
                if (location >= 9 && location <= 16) {
                    int a = location - 9;
                    m += (0x01 << a);
                } else if (location >= 17 && location <= 24) {
                    int a = location - 17;
                    n += (0x01 << a);
                } else if (location >= 25 && location <= 32) {
                    int a = location - 25;
                    o += (0x01 << a);
                } else if (location >= 1 && location <= 8) {
                    int a = location - 1;
                    l += (0x01 << a);
                }
                int finalL = l;
                int finalM = m;
                int finalN = n;
                int finalO = o;
                mHfCardDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String data = String.format(mSetLampFlashing, integerToHexString(moduleId), integerToHexString(finalL), integerToHexString(finalM), integerToHexString(finalN), integerToHexString(finalO));
                        String crcXor = makeChecksum(data);
                        if (listener != null) listener.onHfCardSend(hexHeadXg + data + crcXor);
                        hfCardManager.sendPacket(hexString2Bytes(hexHeadXg + data + crcXor));
                    }
                }, 130 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块指定位置灯闪烁 仅为FG型号可用
     *
     * @param openMap Map<高频模块地址,闪烁位置置集合>
     * @return
     */
    public void setLampFlashing(final Map<Integer, List<Integer>> openMap) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int key : openMap.keySet()) {
                    int m, n, l, s;
                    m = n = l = s = 0;
                    for (int location : openMap.get(key)) {
                        if (location == 0) {
                            location = 32;
                        }
                        if (location >= 9 && location <= 16) {
                            int a = location - 9;
                            m += (0x01 << a);
                        } else if (location >= 17 && location <= 24) {
                            int a = location - 17;
                            n += (0x01 << a);
                        } else if (location >= 25 && location <= 32) {
                            int a = location - 25;
                            s += (0x01 << a);
                        } else if (location >= 1 && location <= 8) {
                            int a = location - 1;
                            l += (0x01 << a);
                        }
                    }
                    int finalL = l;
                    int finalM = m;
                    int finalN = n;
                    int finalS = s;
                    mHfCardDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String data = String.format(mSetLampFlashing, integerToHexStringToAA(key), integerToHexStringToAA(finalL), integerToHexStringToAA(finalM), integerToHexStringToAA(finalN), integerToHexStringToAA(finalS));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHeadXg + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHeadXg + data + crcXor));
                        }
                    }, 130 * time);
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块指定位置灯停止闪烁 仅为XG型号可用
     *
     * @param moduleId 高频模块地址
     * @param location 闪烁位置
     * @return
     */
    public void setLampDefault(final int moduleId, final int location) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                int m, n, l, o;
                m = n = l = o = 0;
                if (location >= 9 && location <= 16) {
                    int a = location - 9;
                    m += (0x01 << a);
                } else if (location >= 17 && location <= 24) {
                    int a = location - 17;
                    n += (0x01 << a);
                } else if (location >= 25 && location <= 32) {
                    int a = location - 25;
                    o += (0x01 << a);
                } else if (location >= 1 && location <= 8) {
                    int a = location - 1;
                    l += (0x01 << a);
                }
                int finalL = l;
                int finalM = m;
                int finalN = n;
                int finalO = o;
                mHfCardDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String data = String.format(mSetLampDefault, integerToHexString(moduleId), integerToHexString(finalL), integerToHexString(finalM), integerToHexString(finalN), integerToHexString(finalO));
                        String crcXor = makeChecksum(data);
                        if (listener != null) listener.onHfCardSend(hexHeadXg + data + crcXor);
                        hfCardManager.sendPacket(hexString2Bytes(hexHeadXg + data + crcXor));
                    }
                }, 130 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块指定位置灯停止闪烁 仅为FG型号可用
     *
     * @param openMap Map<高频模块地址,闪烁位置置集合>
     * @return
     */
    public void setLampDefault(final Map<Integer, List<Integer>> openMap) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int key : openMap.keySet()) {
                    int m, n, l, s;
                    m = n = l = s = 0;
                    for (int location : openMap.get(key)) {
                        if (location == 0) {
                            location = 32;
                        }
                        if (location >= 9 && location <= 16) {
                            int a = location - 9;
                            m += (0x01 << a);
                        } else if (location >= 17 && location <= 24) {
                            int a = location - 17;
                            n += (0x01 << a);
                        } else if (location >= 25 && location <= 32) {
                            int a = location - 25;
                            s += (0x01 << a);
                        } else if (location >= 1 && location <= 8) {
                            int a = location - 1;
                            l += (0x01 << a);
                        }
                    }
                    int finalL = l;
                    int finalM = m;
                    int finalN = n;
                    int finalS = s;
                    mHfCardDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String data = String.format(mSetLampDefault, integerToHexStringToAA(key), integerToHexStringToAA(finalL), integerToHexStringToAA(finalM), integerToHexStringToAA(finalN), integerToHexStringToAA(finalS));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHeadXg + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHeadXg + data + crcXor));
                        }
                    }, 130 * time);
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块查询门锁状态 仅为XG型号可用
     *
     * @param moduleId 高频模块地址
     * @return
     */
    public void hfSearchDoorState(int moduleId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                mHfCardDeviceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        String data = String.format(mSearchDoorState, integerToHexString(moduleId));
                        String crcXor = makeChecksum(data);
                        if (listener != null) listener.onLockDeviceSend(data + crcXor);
                        hfCardManager.sendPacket(hexString2Bytes(data + crcXor));
                    }
                }, 100 * time); // 延时时间逐步增加
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }

    /**
     * 高频模块查询门锁状态 仅为XG型号可用
     *
     * @param moduleIdList 高频模块地址集合
     * @return
     */
    public void hfSearchDoorState(List<Integer> moduleIdList) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare(); // 为当前线程初始化Looper
                mHfCardDeviceHandler = new Handler(Looper.myLooper()); // 创建Handler
                int time = 0;
                for (int moduleId : moduleIdList) {
                    mHfCardDeviceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String data = String.format(mSearchDoorState, integerToHexStringToAA(moduleId));
                            String crcXor = makeChecksum(data);
                            if (listener != null) listener.onHfCardSend(hexHead + data + crcXor);
                            hfCardManager.sendPacket(hexString2Bytes(hexHead + data + crcXor));
                        }
                    }, 130 * time);
                    time++;
                }
                Looper.loop(); // 开始循环处理消息队列
            }
        }).start();
    }


    /**
     * 将字符串按指定长度分割成 String 数组
     *
     * @param input  输入字符串
     * @param length 每个子字符串的长度
     * @return 分割后的 String 数组
     */
    private static String[] splitString(String input, int length) {
        if (input == null || length <= 0) {
            throw new IllegalArgumentException("Input string can't be null and length should be greater than 0");
        }

        int inputLength = input.length();
        int arraySize = (int) Math.ceil((double) inputLength / length);
        String[] result = new String[arraySize];

        for (int i = 0; i < arraySize; i++) {
            int startIndex = i * length;
            int endIndex = Math.min(startIndex + length, inputLength);
            result[i] = input.substring(startIndex, endIndex);
        }

        return result;
    }

    private static boolean checkHexStringIsEquals(String hex1, String hex2) {
        if (Integer.parseInt(hex1, 16) == Integer.parseInt(hex2, 16)) {
            return true;
        }
        return false;
    }

    private static String makeChecksum(String data) {
        if (data == null || data.equals("")) {
            return "";
        }
        int total = 0;
        int len = data.length();
        int num = 0;
        while (num < len) {
            String s = data.substring(num, num + 2);
//            System.out.println(s);
            total += Integer.parseInt(s, 16);
            num = num + 2;
        }
        /**
         * 用256求余最大是255，即16进制的FF
         */
        int mod = total % 256;
        String hex = Integer.toHexString(mod);
        len = hex.length();
        // 如果不够校验位的长度，补0,这里用的是两位校验
        if (len < 2) {
            hex = "0" + hex;
        }
        return hex;
    }

    private String bytesToHexString(byte[] src, int size) {
        String ret = "";
        if (src == null || size <= 0) {
            return null;
        }
        for (int i = 0; i < size; i++) {
            String hex = Integer.toHexString(src[i] & 0xFF);
            if (hex.length() < 2) {
                hex = "0" + hex;
            }
            ret += hex;
        }
        return ret.toUpperCase();
    }

    private byte[] hexString2Bytes(String src) {
        byte[] ret = new byte[src.length() / 2];
        byte[] tmp = src.getBytes();
        for (int i = 0; i < tmp.length / 2; i++) {
            ret[i] = uniteBytes(tmp[i * 2], tmp[i * 2 + 1]);
        }
        return ret;
    }

    private byte uniteBytes(byte src0, byte src1) {
        byte _b0 = Byte.decode("0x" + new String(new byte[]{src0}));
        _b0 = (byte) (_b0 << 4);
        byte _b1 = Byte.decode("0x" + new String(new byte[]{src1}));
        byte ret = (byte) (_b0 ^ _b1);
        return ret;
    }

    /**
     * 计算数据的CRC-16/MODBUS校验码
     *
     * @param data 需要计算的字节数组
     * @return CRC-16/MODBUS校验码
     */
    private String calculateCRC16Modbus(byte[] data) {
        int crc = 0xFFFF; // 初始值
        for (byte b : data) {
            crc ^= (b & 0xFF); // 将byte转为无符号整数后与crc异或
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >> 1) ^ 0xA001; // 0xA001是CRC-16/MODBUS的多项式
                } else {
                    crc >>= 1;
                }
            }
        }
        String crcHexString = Integer.toHexString(crc).toUpperCase();
        while (crcHexString.length() < 4) {
            crcHexString = "0" + crcHexString;
        }
        return crcHexString.substring(2, 4) + crcHexString.substring(0, 2);
    }

    /**
     * 将十六进制字符串转换为十进制字符串
     *
     * @param hexString 十六进制字符串
     * @return 十进制字符串
     */
    private String hexToDecimalString(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            throw new IllegalArgumentException("Input hex string cannot be null or empty.");
        }
        try {
            // 将十六进制字符串转换为十进制整数
            int decimalValue = Integer.parseInt(hexString, 16);
            // 将十进制整数转换为字符串
            return String.valueOf(decimalValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex string: " + hexString, e);
        }
    }

    /**
     * 将十进制整数转为十六进制数，并补位
     */
    private String integerToHexString(int s) {
        String ss = Integer.toHexString(s);
        if (ss.length() % 2 != 0) {
            ss = "0" + ss;//0F格式
        }
        return ss.toUpperCase();
    }

    private static String integerToHexStringToAA(int s) {
        String ss = Integer.toHexString(s);
        if (ss.length() % 2 != 0) {
            ss = "0" + ss;//0F格式
        }
        if (TextUtils.equals(ss.toUpperCase(), "AA")) {
            ss = ss + "00";
        }
        return ss.toUpperCase();
    }

    /**
     * 异或校验
     *
     * @param data 十六进制串
     * @return checkData  十六进制串
     */
    private String checkXor(String data) {
        int checkData = 0;
        for (int i = 0; i < data.length(); i = i + 2) {
            //将十六进制字符串转成十进制
            int start = Integer.parseInt(data.substring(i, i + 2), 16);
            //进行异或运算
            checkData = start ^ checkData;
        }
        return integerToHexString(checkData);
    }

    private String hexString2binaryString(String hexString) {
        if (hexString == null || hexString.length() % 2 != 0) {
            return null;
        }
        String bString = "", tmp;
        for (int i = 1; i < hexString.length() + 1; i++) {
            if (i % 2 == 0) {//每隔两个
                String bytes = hexString.substring(i - 2, i);
                tmp = Integer.toBinaryString(Integer.parseInt(bytes, 16));
                tmp = tmp.trim();
                if (tmp.length() < 8) {
                    int b = 8 - tmp.length();
                    for (int x = 0; x < b; x++) {
                        tmp = "0" + tmp;
                    }
                }
                bString += tmp;
            }
        }
        String mbString = bString;
        String a = "";
        for (int i = mbString.length(); i > 0; i--) {
            a += String.valueOf(mbString.charAt(i - 1));
        }
        return a;
    }

    public void setListener(Callback listener) {
        this.listener = listener;
    }

    public interface Callback {
        public void onHfCardSend(String command);

        public void onLockDeviceSend(String command);

        public void onHfCardReception(String command);

        public void onHfCardReceptionDoorStates(Map<Integer, String> hfCardDoorStateMap);

        public void onHfCardReceptionCards(Map<Integer, String[]> hfCardsMap);

        public void onLockDeviceReception(String command);

        public void onLockDeviceReceptionDoorStates(Map<Integer, String[]> lockDeviceDoorStateMap);

        public void onIdCardReception(String command);
    }
}