/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.output;

import heronarts.lx.output.LXOutput.InetOutput;

public interface OPCOutput extends InetOutput {

  public static final int DEFAULT_PORT = 7890;

  public static final int HEADER_LEN = 4;

  public static final int OFFSET_CHANNEL = 0;
  public static final int OFFSET_COMMAND = 1;
  public static final int OFFSET_DATA_LEN_MSB = 2;
  public static final int OFFSET_DATA_LEN_LSB = 3;
  public static final int OFFSET_DATA = 4;

  public static final byte CHANNEL_BROADCAST = 0;

  public static final byte COMMAND_SET_PIXEL_COLORS = 0;
  public static final byte COMMAND_SYSTEM_EXCLUSIVE = (byte) 0xff;


  public OPCOutput setChannel(byte opcChannel);
}
