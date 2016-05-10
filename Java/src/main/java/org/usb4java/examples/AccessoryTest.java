package org.usb4java.examples;

import static java.lang.Thread.sleep;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.usb4java.BufferUtils;
import org.usb4java.ConfigDescriptor;
import org.usb4java.Context;
import org.usb4java.DescriptorUtils;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.EndpointDescriptor;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

public class AccessoryTest {
    //vendor ID for Samsung Phone
    private final static short VENDOR_ID = (short) 0x4e8;
    
    private final static short VENDOR_ID_ACCESSORY = (short) 0x18D1;
    
    private static byte IN_ENDPOINT = (byte) 0x00;
    private static byte OUT_ENDPOINT = 0x00;
    
    //control request type
    private final static short CTRL_TYPE_STANDARD = (0 << 5);
    private final static short CTRL_TYPE_CLASS = (1 << 5);
    private final static short CTRL_TYPE_VENDOR = (2 << 5);
    private final static short CTRL_TYPE_RESERVED = (3 << 5);
    
    //control request direction
    private final static short CTRL_OUT = 0x00;
    private final static short CTRL_IN = 0x80;
    
    /** The communication timeout in milliseconds. */
    private static final int TIMEOUT = 5000;
    
    private static Context context;
    private static Device device;
    private static DeviceHandle handle;
    private static boolean isShutdown = false;

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                isShutdown = true;
            };
        });
        try {
            if(init(VENDOR_ID) == LibUsb.SUCCESS)
                setupAccessory("Hello Inc", "HelloWorldModel", "Description", "1.0", "http://www.mycompany.com",
                    "SerialNumber");

            //if (result != LibUsb.SUCCESS)
            //    throw new LibUsbException("Unable to setup Accessory", result);
            sleep(1000);
            init(VENDOR_ID_ACCESSORY);
            //endpointDescriptor.
            getEndPointAddress();
            write(handle);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(handle != null){
                LibUsb.releaseInterface(handle, 0);
                LibUsb.close(handle);
            }
            LibUsb.exit(context);
        }

    }

    private static void getEndPointAddress(){
        ConfigDescriptor configDescriptor = new ConfigDescriptor();
        LibUsb.getActiveConfigDescriptor(device, configDescriptor);
        EndpointDescriptor[] endPoints = (configDescriptor.iface()[0]).altsetting()[0].endpoint();
        for(EndpointDescriptor end : endPoints){
            if(DescriptorUtils.getDirectionName(end.bEndpointAddress()) == "IN"){
                IN_ENDPOINT = (byte)(end.bEndpointAddress() & 0xff);
                System.out.printf("IN = " + String.format("0x%02x", IN_ENDPOINT) + "\n");
                
            }
            if(DescriptorUtils.getDirectionName(end.bEndpointAddress()) == "OUT"){
                OUT_ENDPOINT = (byte)(end.bEndpointAddress() & 0xff);
                System.out.printf("OUT = " + String.format("0x%02x", OUT_ENDPOINT)  + "\n");
            }
        }
    }
    private static int init(short vendor_id) {
        context = new Context();
        int result = LibUsb.init(context);
        if (result != LibUsb.SUCCESS)
            throw new LibUsbException("Unable to initialize libusb.", result);

        device = findDevice(vendor_id);
        if(device == null && vendor_id == VENDOR_ID){
                System.out.println("Could not find out android device, try to open Acessory device");
                return -1;
        }
        handle = new DeviceHandle();

        result = LibUsb.open(device, handle);
        if (result != LibUsb.SUCCESS){
            throw new LibUsbException("Unable to open USB device", result);
        }

        result = LibUsb.claimInterface(handle, 0);
        if (result != LibUsb.SUCCESS)
            throw new LibUsbException("Unable to claim interface", result);
        return LibUsb.SUCCESS;
    }

    private static Device findDevice(short vendorId) {
        // Read the USB device list
        DeviceList list = new DeviceList();
        int result = LibUsb.getDeviceList(null, list);
        if (result < 0)
            throw new LibUsbException("Unable to get device list", result);

        try {
            // Iterate over all devices and scan for the right one
            for (Device device : list) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, descriptor);
                if (result != LibUsb.SUCCESS)
                    throw new LibUsbException("Unable to read device descriptor", result);
                if (descriptor.idVendor() == vendorId)
                    return device;
            }
        } finally {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(list, false);
        }

        // Device not found
        return null;
    }

    private static int setupAccessory(String vendor, String model, String description, String version, String url,
            String serial) throws LibUsbException {

        int response = 0;

        // Setup setup token
        response = transferSetupPacket((byte) (CTRL_TYPE_VENDOR | CTRL_IN), (byte) 51);

        // Setup data packet
        response = transferAccessoryDataPacket(vendor, (short) 0);
        response = transferAccessoryDataPacket(model, (short) 1);
        response = transferAccessoryDataPacket(description, (short) 2);
        response = transferAccessoryDataPacket(version, (short) 3);
        response = transferAccessoryDataPacket(url, (short) 4);
        response = transferAccessoryDataPacket(serial, (short) 5);

        // Setup handshake packet
        response = transferSetupPacket((byte) (CTRL_TYPE_VENDOR | CTRL_OUT), (byte) 53);

        LibUsb.releaseInterface(handle, 0);

        return response;
    }

    private static int transferSetupPacket(byte requestType, byte request) throws LibUsbException {
        int response = 0;
        byte[] bytebuff = new byte[2];
        ByteBuffer data = BufferUtils.allocateByteBuffer(bytebuff.length);
        data.put(bytebuff);

        final short wValue = 0;
        final short wIndex = 0;
        final long timeout = 0;

        response = LibUsb.controlTransfer(handle, requestType, request, wValue, wIndex,
                data, timeout);

        //if(response < 0)
        //    throw new LibUsbException("Unable to transfer setup packet ", response);

        return response;
    }

    private static int transferAccessoryDataPacket(String param, short index) {
        int response;
        byte[] byteArray = param.getBytes();
        ByteBuffer data = BufferUtils.allocateByteBuffer(byteArray.length);
        data.put(byteArray);
        final byte bRequest = (byte) 52;
        final short wValue = 0;
        final long timeout = 0;
        response = LibUsb.controlTransfer(handle, (byte) (CTRL_TYPE_VENDOR | CTRL_OUT), bRequest, wValue, index,
                data, timeout);
        if(response < 0)
            throw new LibUsbException("Unable to control transfer.", response);
        return response;
    }

    public static void write(DeviceHandle handle)
    {
        int data = 0;
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(1);
        while (!isShutdown){
            buffer.rewind();
            buffer.put((byte) data);
            IntBuffer transferred = BufferUtils.allocateIntBuffer();
            int result = LibUsb.bulkTransfer(handle, OUT_ENDPOINT, buffer,
                transferred, TIMEOUT);
            if (result != LibUsb.SUCCESS)
            {
                throw new LibUsbException("Unable to send data", result);
            }
            System.out.println(transferred.get() + " bytes sent to device");
            if(data < 255){
                data = data + 1;
            }else{
                data = 0;
            }
            try {
                sleep(50);
            } catch (InterruptedException ex) {
                Logger.getLogger(AccessoryTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Reads some data from the device.
     * 
     * @param handle
     *            The device handle.
     * @param size
     *            The number of bytes to read from the device.
     * @return The read data.
     */
    public static ByteBuffer read(DeviceHandle handle, int size)
    {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(size).order(
            ByteOrder.LITTLE_ENDIAN);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(handle, IN_ENDPOINT, buffer,
            transferred, TIMEOUT);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to read data", result);
        }
        System.out.println(transferred.get() + " bytes read from device");
        return buffer;
    }
}