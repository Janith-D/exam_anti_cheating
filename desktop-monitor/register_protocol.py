"""
Windows Protocol Handler Registration Script
Registers the desktop-monitor:// protocol with Windows
"""

import winreg
import os
import sys
from pathlib import Path


def register_protocol_handler(protocol_name="desktop-monitor"):
    """Register custom protocol handler in Windows Registry"""
    
    # Get the path to the Python executable and the app script
    python_exe = sys.executable
    app_path = Path(__file__).parent / "app.py"
    
    # Command to execute when protocol is invoked
    command = f'"{python_exe}" "{app_path}" --url "%1"'
    
    try:
        # Create registry key for protocol
        key_path = f"Software\\Classes\\{protocol_name}"
        
        # Create main protocol key
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, key_path) as key:
            winreg.SetValue(key, "", winreg.REG_SZ, f"URL:{protocol_name} Protocol")
            winreg.SetValueEx(key, "URL Protocol", 0, winreg.REG_SZ, "")
        
        # Create DefaultIcon key
        icon_path = f"{key_path}\\DefaultIcon"
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, icon_path) as key:
            winreg.SetValue(key, "", winreg.REG_SZ, python_exe + ",0")
        
        # Create shell\open\command key
        command_path = f"{key_path}\\shell\\open\\command"
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, command_path) as key:
            winreg.SetValue(key, "", winreg.REG_SZ, command)
        
        print(f"✓ Protocol '{protocol_name}://' registered successfully!")
        print(f"  Command: {command}")
        print(f"\nYou can now use URLs like: {protocol_name}://login?token=XXX&studentId=123")
        
        return True
        
    except Exception as e:
        print(f"✗ Error registering protocol: {e}")
        return False


def unregister_protocol_handler(protocol_name="desktop-monitor"):
    """Unregister custom protocol handler from Windows Registry"""
    
    try:
        key_path = f"Software\\Classes\\{protocol_name}"
        winreg.DeleteKey(winreg.HKEY_CURRENT_USER, key_path + "\\shell\\open\\command")
        winreg.DeleteKey(winreg.HKEY_CURRENT_USER, key_path + "\\shell\\open")
        winreg.DeleteKey(winreg.HKEY_CURRENT_USER, key_path + "\\shell")
        winreg.DeleteKey(winreg.HKEY_CURRENT_USER, key_path + "\\DefaultIcon")
        winreg.DeleteKey(winreg.HKEY_CURRENT_USER, key_path)
        
        print(f"✓ Protocol '{protocol_name}://' unregistered successfully!")
        return True
        
    except FileNotFoundError:
        print(f"Protocol '{protocol_name}://' is not registered")
        return False
    except Exception as e:
        print(f"✗ Error unregistering protocol: {e}")
        return False


def check_protocol_registered(protocol_name="desktop-monitor"):
    """Check if protocol is registered"""
    
    try:
        key_path = f"Software\\Classes\\{protocol_name}"
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, key_path):
            print(f"✓ Protocol '{protocol_name}://' is registered")
            return True
    except FileNotFoundError:
        print(f"✗ Protocol '{protocol_name}://' is not registered")
        return False
    except Exception as e:
        print(f"Error checking protocol: {e}")
        return False


def main():
    """Main entry point"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Protocol Handler Registration')
    parser.add_argument('action', choices=['register', 'unregister', 'check'], 
                       help='Action to perform')
    parser.add_argument('--protocol', default='desktop-monitor', 
                       help='Protocol name (default: desktop-monitor)')
    
    args = parser.parse_args()
    
    print(f"\n{'='*60}")
    print(f"  Desktop Monitor Protocol Handler")
    print(f"{'='*60}\n")
    
    if args.action == 'register':
        register_protocol_handler(args.protocol)
    elif args.action == 'unregister':
        unregister_protocol_handler(args.protocol)
    elif args.action == 'check':
        check_protocol_registered(args.protocol)
    
    print()


if __name__ == "__main__":
    main()
