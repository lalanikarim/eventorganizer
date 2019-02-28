<nav class="navbar navbar-expand-md navbar-light bg-light fixed-top mb-4 d-print-none">
    <div class="container">
    <a class="navbar-brand" href="/">{{ config('app.name') }}</a>
    <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarCollapse" aria-controls="navbarCollapse" aria-expanded="false" aria-label="Toggle navigation">
        <span class="navbar-toggler-icon"></span>
    </button>
    <div class="collapse navbar-collapse" id="navbarCollapse">
        <ul class="navbar-nav mr-auto">

            @auth
            <li class="nav-item">
                <form class="form-inline mt-2 mt-md-0" action="{{ route('search-donor') }}" method="post">
                    @csrf
                    <input class="form-control mr-sm-2" type="text" placeholder="Search" name="searchDonors" value="{{ $searchDonors ?? ''}}" aria-label="Search">
                    <button class="btn btn-primary my-2 my-sm-0" type="submit">Search</button>
                </form>
            </li>
            @endauth
        </ul>
        <ul class="navbar-nav">
            @guest
            <li class="nav-item">
                <a href="{{ route('login') }}" class="nav-link">Login</a>
            </li>
            @else
            <li class="nav-item">
                <div class="dropdown">
                    <button class="btn btn-outline-primary dropdown-toggle" type="button" id="dropdownMenuButton" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                        {{ auth()->user()->name }}
                    </button>
                    <div class="dropdown-menu" aria-labelledby="dropdownMenuButton">
                        <form id="frmLogout" action="{{ route('logout') }}" method="POST">
                            {{ csrf_field()  }}
                        <button class="dropdown-item" type="submit">Logout</button>
                        </form>
                    </div>
                </div>
            </li>
            @endguest
        </ul>
    </div>
    </div>
</nav>