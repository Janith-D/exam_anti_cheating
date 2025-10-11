import sys, importlib, traceback, pkgutil
print('sys.path[0:5]=', sys.path[:5])
try:
    import flask
    print('flask repr:', flask)
    print('flask __file__:', getattr(flask,'__file__',None))
    print('flask __spec__:', getattr(flask,'__spec__',None))
    print('flask __path__:', getattr(flask,'__path__',None))
    try:
        from flask import Flask
        print('Imported Flask:', Flask)
    except Exception:
        print('Failed importing Flask from flask:')
        traceback.print_exc()
except Exception:
    print('Failed importing flask:')
    traceback.print_exc()
print('pkgutil.get_loader("flask"):', pkgutil.get_loader('flask'))
print('End debug')
