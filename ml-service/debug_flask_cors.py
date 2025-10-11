import sys, traceback, pkgutil
print('sys.path[0:5]=', sys.path[:5])
try:
    import flask_cors
    print('flask_cors repr:', flask_cors)
    print('flask_cors __file__:', getattr(flask_cors,'__file__',None))
    print('flask_cors __spec__:', getattr(flask_cors,'__spec__',None))
    try:
        from flask_cors import CORS
        print('Imported CORS:', CORS)
    except Exception:
        print('Failed importing CORS')
        traceback.print_exc()
except Exception:
    print('Failed importing flask_cors')
    traceback.print_exc()
print('pkgutil.get_loader("flask_cors"):', pkgutil.get_loader('flask_cors'))
print('End debug')
